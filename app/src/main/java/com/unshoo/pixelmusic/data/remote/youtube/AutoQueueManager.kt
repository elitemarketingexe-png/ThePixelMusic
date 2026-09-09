package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.unshoo.pixelmusic.data.remote.youtube.queue.EmptyQueue
import com.unshoo.pixelmusic.data.remote.youtube.queue.Queue
import com.unshoo.pixelmusic.data.remote.youtube.queue.ResilientRadioQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.math.absoluteValue

import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.MusicDao

object AutoQueueManager {
    private const val TAG = "AutoQueueMgr"
    private const val REFILL_THRESHOLD = 8
    private const val STARTUP_SAFETY_CHECK_DELAY_MS = 2_500L
    private const val SETTINGS_READ_TIMEOUT_MS = 3_000L

    @Volatile private var currentQueue: Queue = EmptyQueue

    private val refillMutex = Mutex()
    @Volatile private var refillRequestedAgain = false

    private val internalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var musicDaoRef: MusicDao? = null
    private var engagementDaoRef: com.unshoo.pixelmusic.data.database.EngagementDao? = null
    private var onQueueItemsAddedCallback: (() -> Unit)? = null

    private fun getActiveScope(): CoroutineScope {
        val s = scope
        return if (s != null && s.isActive) s else internalScope
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            Handler(Looper.getMainLooper()).post(action)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            Timber.tag(TAG).d("onMediaItemTransition reason=%d mediaId=%s", reason, mediaItem.mediaId)
            checkAndRefill()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                Timber.tag(TAG).d("onTimelineChanged(PLAYLIST_CHANGED), windowCount=%d", timeline.windowCount)
                checkAndRefill()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Timber.tag(TAG).d("STATE_ENDED — forcing immediate refill")
                checkAndRefill(forceImmediate = true)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) checkAndRefill()
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao,
        engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao,
        onQueueItemsAdded: (() -> Unit)? = null
    ) {
        runOnMain {
            scope = coroutineScope
            contextRef = context.applicationContext
            datastoreRepository = datastoreRepo
            playerRef = player
            musicDaoRef = musicDao
            engagementDaoRef = engagementDao
            onQueueItemsAddedCallback = onQueueItemsAdded
            player.addListener(playerListener)
            Timber.tag(TAG).d("Attached to player")

            checkAndRefill()

            getActiveScope().launch(Dispatchers.IO) {
                delay(STARTUP_SAFETY_CHECK_DELAY_MS)
                checkAndRefill()
            }
        }
    }

    fun updatePlayer(newPlayer: Player) {
        runOnMain {
            val oldPlayer = playerRef
            if (oldPlayer !== newPlayer) {
                oldPlayer?.removeListener(playerListener)
                playerRef = newPlayer
                newPlayer.addListener(playerListener)
                Timber.tag(TAG).d("Player updated")
                checkAndRefill()
            }
        }
    }

    fun detach(player: Player?) {
        runOnMain {
            playerRef?.removeListener(playerListener)
            player?.removeListener(playerListener)
            playerRef = null
            scope = null
            contextRef = null
            datastoreRepository = null
            musicDaoRef = null
            engagementDaoRef = null
            onQueueItemsAddedCallback = null
            Timber.tag(TAG).d("Detached")
        }
    }

    fun reset() {
        Timber.tag(TAG).d("reset() called")
        currentQueue = EmptyQueue
    }

    fun disableAndTrimQueue() {
        reset()
        getActiveScope().launch(Dispatchers.Main) {
            val player = playerRef ?: return@launch
            val totalCount = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (totalCount <= currentIndex + 1) return@launch
            player.removeMediaItems(currentIndex + 1, totalCount)
            Timber.tag(TAG).d("disableAndTrimQueue removed %d upcoming tracks", totalCount - currentIndex - 1)
        }
    }

    fun resetAndReseedFromCurrentSong() {
        Timber.tag(TAG).d("resetAndReseedFromCurrentSong()")
        reset()
        val player = playerRef ?: return
        getActiveScope().launch(Dispatchers.IO) {
            try {
                if (!isAutoQueueEnabled()) {
                    Timber.tag(TAG).d("AutoQueue disabled, skipping reseed")
                    return@launch
                }
                val currentId = withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId } ?: return@launch
                seedFromMediaId(currentId)
                checkAndRefill(forceImmediate = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "resetAndReseedFromCurrentSong failed")
            }
        }
    }

    fun seed(endpoint: WatchEndpoint, continuation: String?, videoId: String) {
        val dao = musicDaoRef ?: return
        currentQueue = ResilientRadioQueue.resumed(
            endpoint = endpoint,
            continuation = continuation,
            musicDao = dao,
            isOnline = ::hasInternet,
        )
        Timber.tag(TAG).d("seed() videoId=%s hasContinuation=%b", videoId, continuation != null)
        getActiveScope().launch(Dispatchers.IO) { ensureLocalSeed(videoId) }
    }

    fun scheduleAdaptiveRefill(delayMs: Long? = null, forceRefresh: Boolean = false) {
        getActiveScope().launch(Dispatchers.IO) {
            if (delayMs != null && delayMs > 0L) delay(delayMs)
            if (forceRefresh) {
                val player = playerRef
                val currentId = withContext(Dispatchers.Main) { player?.currentMediaItem?.mediaId }
                if (currentId != null) seedFromMediaId(currentId)
            }
            doRefill()
        }
    }

    fun scheduleRefill(delayMs: Long = 0L, forceRefresh: Boolean = false) {
        scheduleAdaptiveRefill(delayMs = if (delayMs > 0L) delayMs else null, forceRefresh = forceRefresh)
    }

    fun forceRefill(forceRefresh: Boolean) {
        scheduleAdaptiveRefill(delayMs = null, forceRefresh = forceRefresh)
    }

    fun registerSkip(songId: String, artistName: String? = null) {
        checkAndRefill()
    }

    suspend fun buildMixQueue(seedSong: Song, onlineRelated: List<Song>): List<Song> {
        return if (onlineRelated.isNotEmpty()) {
            (listOf(seedSong) + onlineRelated).distinctBy { it.youtubeId ?: it.id }
        } else {
            listOf(seedSong)
        }
    }

    private fun checkAndRefill(forceImmediate: Boolean = false) {
        getActiveScope().launch(Dispatchers.IO) {
            try {
                if (!isAutoQueueEnabled()) return@launch

                val remaining = withContext(Dispatchers.Main) {
                    val p = playerRef ?: return@withContext -1
                    computeRemainingUpcoming(p)
                }
                if (remaining < 0) return@launch

                if (forceImmediate || remaining <= REFILL_THRESHOLD) {
                    doRefill()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "checkAndRefill error")
            }
        }
    }

    private suspend fun doRefill() {
        if (!refillMutex.tryLock()) {
            refillRequestedAgain = true
            return
        }
        try {
            do {
                refillRequestedAgain = false
                doRefillLocked()
            } while (refillRequestedAgain)
        } finally {
            refillMutex.unlock()
        }
    }

    private suspend fun doRefillLocked() {
        if (playerRef == null) return
        if (!isAutoQueueEnabled()) return

        val remaining = withContext(Dispatchers.Main) {
            val p = playerRef ?: return@withContext -1
            computeRemainingUpcoming(p)
        }
        if (remaining < 0) return
        if (remaining > REFILL_THRESHOLD) return

        if (currentQueue === EmptyQueue) {
            val currentId = withContext(Dispatchers.Main) { playerRef?.currentMediaItem?.mediaId }
            if (currentId != null) seedFromMediaId(currentId)
        }

        val activeQueue = currentQueue
        if (activeQueue === EmptyQueue || !activeQueue.hasNextPage()) return

        val existingIds = withContext(Dispatchers.Main) {
            val p = playerRef ?: return@withContext emptySet<String>()
            (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
        }

        val fetched = try {
            activeQueue.nextPage()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "nextPage() failed")
            return
        }

        val newItems = fetched.filterNot { it.mediaId in existingIds }
        if (newItems.isEmpty()) return

        withContext(Dispatchers.Main) {
            playerRef?.addMediaItems(newItems)
            onQueueItemsAddedCallback?.invoke()
        }
        Timber.tag(TAG).d("Refill added %d items (remaining was %d)", newItems.size, remaining)
    }

    private suspend fun seedFromMediaId(mediaId: String) {
        val dao = musicDaoRef ?: return
        val videoId = resolveVideoId(mediaId)
        if (videoId == null) {
            currentQueue = ResilientRadioQueue.radio(videoId = null, musicDao = dao, isOnline = ::hasInternet)
            ensureLocalSeed(mediaId)
            return
        }
        currentQueue = ResilientRadioQueue.radio(videoId = videoId, musicDao = dao, isOnline = ::hasInternet)
        ensureLocalSeed(videoId)
    }

    private suspend fun ensureLocalSeed(idOrMediaId: String) {
        val dao = musicDaoRef ?: return
        val queue = currentQueue
        if (queue !is ResilientRadioQueue) return
        val entity = withContext(Dispatchers.IO) {
            val longId = idOrMediaId.toLongOrNull() ?: getDatabaseIdForYoutubeId(normalizeId(idOrMediaId))
            runCatching { dao.getSongByIdOnce(longId) }.getOrNull()
        }
        queue.updateLocalSeed(entity)
    }

    private suspend fun resolveVideoId(mediaId: String): String? {
        val clean = normalizeId(mediaId)
        if (clean.length == 11 && clean.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) return clean
        val dao = musicDaoRef ?: return null
        val longId = mediaId.toLongOrNull() ?: return null
        val entity = runCatching { dao.getSongByIdOnce(longId) }.getOrNull() ?: return null
        val uri = entity.contentUriString
        return if (uri.startsWith("youtube://")) uri.removePrefix("youtube://") else null
    }

    private fun normalizeId(id: String): String = when {
        id.startsWith("youtube_") -> id.substringAfter("youtube_")
        id.startsWith("youtube://") -> id.substringAfter("youtube://")
        else -> id
    }

    private suspend fun isAutoQueueEnabled(): Boolean {
        val repo = datastoreRepository ?: return true
        return withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) {
            repo.settings.first().autoQueueEnabled
        } ?: true
    }

    private fun hasInternet(): Boolean {
        val ctx = contextRef ?: return false
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }

    fun computeRemainingUpcoming(player: Player): Int {
        val timeline = player.currentTimeline
        val count = timeline.windowCount
        if (count == 0) return 0
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return 0
        if (!player.shuffleModeEnabled) {
            return (count - currentIndex - 1).coerceAtLeast(0)
        }
        var remaining = 0
        var windowIndex = timeline.getNextWindowIndex(currentIndex, Player.REPEAT_MODE_OFF, true)
        while (windowIndex != C.INDEX_UNSET && remaining < count) {
            remaining++
            windowIndex = timeline.getNextWindowIndex(windowIndex, Player.REPEAT_MODE_OFF, true)
        }
        return remaining
    }

    fun getDatabaseIdForYoutubeId(youtubeId: String): Long {
        val youtubeSongIdOffset = 15_000_000_000_000L
        return -(youtubeSongIdOffset + youtubeId.hashCode().toLong().absoluteValue)
    }
}
