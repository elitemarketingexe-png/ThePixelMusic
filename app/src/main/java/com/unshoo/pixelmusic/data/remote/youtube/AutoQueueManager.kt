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
    private const val REFILL_THRESHOLD = 20
    private const val TARGET_UPCOMING_COUNT = 40
    private const val MAX_REFILL_BATCH_ATTEMPTS = 5
    private const val STARTUP_SAFETY_CHECK_DELAY_MS = 2_500L
    private const val SETTINGS_READ_TIMEOUT_MS = 3_000L

    @Volatile private var currentQueue: Queue = EmptyQueue

    private val refillMutex = Mutex()
    @Volatile private var refillRequestedAgain = false

    private val internalScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null
    private var datastoreRepository: DatastoreRepository? = null

    // playerRef is kept ONLY for listener add/remove bookkeeping (you must call
    // addListener/removeListener on the exact instance that has the listener).
    // It is NOT used for reading state anymore — see livePlayer() below.
    private var playerRef: Player? = null

    // The dual-player crossfade engine swaps which underlying ExoPlayer instance is
    // "master" on every single transition (see DualPlayerEngine: playerA becomes
    // playerB and vice-versa), and notifies listeners of that swap through several
    // conditional layers (cast-session checks, a pre-crossfade "display player"
    // publish, a post-crossfade publish). If ANY of those layers is delayed or
    // skipped for a given transition, a cached player reference goes stale — and the
    // old instance gets stop()+clearMediaItems()'d moments later. Reading state from
    // a stale reference silently "succeeds" while operating on a dead player nobody
    // is listening to, which looks exactly like "the queue randomly stops
    // populating." playerProvider() sidesteps this entirely: every state read and
    // every addMediaItems() call asks "what is the live player RIGHT NOW", so it is
    // correct even if the swap-notification pipeline is late, skipped, or racy.
    private var playerProvider: (() -> Player)? = null
    private fun livePlayer(): Player? = playerProvider?.invoke() ?: playerRef

    private var musicDaoRef: MusicDao? = null
    private var engagementDaoRef: com.unshoo.pixelmusic.data.database.EngagementDao? = null
    private var onQueueItemsAddedCallback: (() -> Unit)? = null

    @Volatile private var lastReseedRequestAtMs = 0L
    @Volatile private var lastTrimRequestAtMs = 0L
    private const val DUPLICATE_CALL_DEBOUNCE_MS = 400L

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

    /**
     * @param playerProvider Returns whichever player is CURRENTLY the live/master
     *   player at the moment it's called. For a simple single-player setup this can
     *   just be `{ player }`; for a crossfade dual-player engine it should be
     *   `{ engine.masterPlayer }` so every read reflects reality even between swaps.
     */
    fun attach(
        playerProvider: () -> Player,
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
            this.playerProvider = playerProvider
            val initialPlayer = playerProvider()
            playerRef = initialPlayer
            musicDaoRef = musicDao
            engagementDaoRef = engagementDao
            onQueueItemsAddedCallback = onQueueItemsAdded
            initialPlayer.addListener(playerListener)
            Timber.tag(TAG).d("Attached to player")

            checkAndRefill()

            getActiveScope().launch(Dispatchers.IO) {
                delay(STARTUP_SAFETY_CHECK_DELAY_MS)
                checkAndRefill()
            }
        }
    }

    /**
     * Legacy attach overload for single Player reference.
     */
    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao,
        engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao,
        onQueueItemsAdded: (() -> Unit)? = null
    ) {
        attach(
            playerProvider = { player },
            context = context,
            datastoreRepo = datastoreRepo,
            coroutineScope = coroutineScope,
            musicDao = musicDao,
            engagementDao = engagementDao,
            onQueueItemsAdded = onQueueItemsAdded
        )
    }

    /**
     * Re-points the listener after a player swap (crossfade transition, cast
     * connect/disconnect, etc). Only affects listener bookkeeping — state reads
     * already go through [livePlayer] and don't depend on this having fired yet,
     * so a late or occasionally-skipped swap notification can no longer cause a
     * refill to silently operate on a dead player.
     */
    fun updatePlayer(newPlayer: Player) {
        runOnMain {
            val oldPlayer = playerRef
            if (oldPlayer !== newPlayer) {
                oldPlayer?.removeListener(playerListener)
                playerRef = newPlayer
                newPlayer.addListener(playerListener)
                Timber.tag(TAG).d("Player updated")
            }
        }
    }

    fun detach(player: Player?) {
        runOnMain {
            playerRef?.removeListener(playerListener)
            player?.removeListener(playerListener)
            playerRef = null
            playerProvider = null
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
        val now = System.currentTimeMillis()
        if (now - lastTrimRequestAtMs < DUPLICATE_CALL_DEBOUNCE_MS) {
            Timber.tag(TAG).d("disableAndTrimQueue() ignored — duplicate call within %d ms", DUPLICATE_CALL_DEBOUNCE_MS)
            return
        }
        lastTrimRequestAtMs = now
        reset()
        getActiveScope().launch(Dispatchers.Main) {
            val player = livePlayer() ?: return@launch
            val totalCount = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (totalCount <= currentIndex + 1) return@launch
            player.removeMediaItems(currentIndex + 1, totalCount)
            Timber.tag(TAG).d("disableAndTrimQueue removed %d upcoming tracks", totalCount - currentIndex - 1)
        }
    }

    fun resetAndReseedFromCurrentSong() {
        val now = System.currentTimeMillis()
        if (now - lastReseedRequestAtMs < DUPLICATE_CALL_DEBOUNCE_MS) {
            Timber.tag(TAG).d("resetAndReseedFromCurrentSong() ignored — duplicate call within %d ms", DUPLICATE_CALL_DEBOUNCE_MS)
            return
        }
        lastReseedRequestAtMs = now
        Timber.tag(TAG).d("resetAndReseedFromCurrentSong()")
        reset()
        getActiveScope().launch(Dispatchers.IO) {
            try {
                if (!isAutoQueueEnabled()) {
                    Timber.tag(TAG).d("AutoQueue disabled, skipping reseed")
                    return@launch
                }
                val currentId = withContext(Dispatchers.Main) { livePlayer()?.currentMediaItem?.mediaId } ?: return@launch
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
                val currentId = withContext(Dispatchers.Main) { livePlayer()?.currentMediaItem?.mediaId }
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
                    val p = livePlayer() ?: return@withContext -1
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
        if (livePlayer() == null) return
        if (!isAutoQueueEnabled()) return

        var remaining = withContext(Dispatchers.Main) {
            val p = livePlayer() ?: return@withContext -1
            computeRemainingUpcoming(p)
        }
        if (remaining < 0 || remaining > REFILL_THRESHOLD) return

        if (currentQueue === EmptyQueue) {
            val currentId = withContext(Dispatchers.Main) { livePlayer()?.currentMediaItem?.mediaId }
            if (currentId != null) seedFromMediaId(currentId)
        }

        var batchAttempts = 0
        var totalAdded = 0
        var consecutiveEmptyFetches = 0

        while (remaining < TARGET_UPCOMING_COUNT && batchAttempts < MAX_REFILL_BATCH_ATTEMPTS) {
            batchAttempts++
            val activeQueue = currentQueue
            if (activeQueue === EmptyQueue) {
                val seedMediaId = withContext(Dispatchers.Main) {
                    val p = livePlayer() ?: return@withContext null
                    p.currentMediaItem?.mediaId
                        ?: if (p.mediaItemCount > 0) p.getMediaItemAt(p.mediaItemCount - 1).mediaId else null
                }
                if (seedMediaId != null) {
                    seedFromMediaId(seedMediaId)
                } else {
                    break
                }
            }

            val existingIds = withContext(Dispatchers.Main) {
                val p = livePlayer() ?: return@withContext emptySet<String>()
                (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }.toSet()
            }

            val fetched = try {
                currentQueue.nextPage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "nextPage() failed on batch %d", batchAttempts)
                emptyList()
            }

            val newItems = fetched.filterNot { it.mediaId in existingIds }

            if (newItems.isNotEmpty()) {
                consecutiveEmptyFetches = 0
                withContext(Dispatchers.Main) {
                    livePlayer()?.addMediaItems(newItems)
                    onQueueItemsAddedCallback?.invoke()
                }
                totalAdded += newItems.size
                remaining = withContext(Dispatchers.Main) {
                    val p = livePlayer() ?: return@withContext -1
                    computeRemainingUpcoming(p)
                }
                Timber.tag(TAG).d("Batch %d: added %d items -> remaining is now %d", batchAttempts, newItems.size, remaining)
            } else {
                consecutiveEmptyFetches++
                Timber.tag(TAG).w("Batch %d yielded no new items (fetched=%d, existing=%d). Consecutive empty=%d",
                    batchAttempts, fetched.size, existingIds.size, consecutiveEmptyFetches)

                if (consecutiveEmptyFetches >= 2) {
                    // Try reseeding from current playing track or the last track in the player queue
                    val reseedId = withContext(Dispatchers.Main) {
                        val p = livePlayer() ?: return@withContext null
                        p.currentMediaItem?.mediaId
                            ?: if (p.mediaItemCount > 0) p.getMediaItemAt(p.mediaItemCount - 1).mediaId else null
                    }
                    if (reseedId != null) {
                        Timber.tag(TAG).d("Reseeding AutoQueue from song: %s", reseedId)
                        seedFromMediaId(reseedId)
                    } else {
                        break
                    }
                }
                if (consecutiveEmptyFetches >= 4) {
                    break
                }
            }
        }

        if (totalAdded > 0) {
            Timber.tag(TAG).d("doRefillLocked finished: total added %d items, upcoming remaining is now %d", totalAdded, remaining)
        }
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
