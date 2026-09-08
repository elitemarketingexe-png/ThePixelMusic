package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper.printe
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper.printd
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
import kotlin.math.absoluteValue

import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.MusicDao

/**
 * AutoQueueManager — auto-queue engine, rewritten around the Metrolist-derived [Queue]
 * abstraction (see `data.remote.youtube.queue`).
 *
 * WHAT CHANGED FROM THE OLD VERSION AND WHY
 * ------------------------------------------------------------------------------------
 * The old implementation kept its paging state (`continuationToken`, `currentEndpoint`,
 * `lastSeedVideoId`, a rolling dedup deque) as loose top-level `@Volatile` vars shared
 * by the whole process. Every coroutine that touched auto-queue — a transition-based
 * refill, a watchdog tick, a manual reseed from Settings — read and wrote the SAME
 * fields. That's the textbook setup for state going stale or getting stomped on, and it
 * is the root cause of both bugs reported against this file:
 *
 *   1. "Toggling auto-queue off then on sometimes fixes it" — the *manual* reseed path
 *      (`resetAndReseedFromCurrentSong`) always ran well after startup, once the player
 *      and settings were fully settled, so it never raced anything. The *automatic*
 *      path only had ONE real trigger (`onMediaItemTransition`) plus a mutex that
 *      silently dropped a refill request if it arrived while another was in flight —
 *      no retry, no memory that a refill was still owed. Toggling was really just a
 *      manual "try again once everything has settled" — which is exactly what the
 *      automatic path should be doing on its own.
 *
 *   2. "Force-stopping the app breaks it" — Force Stop always kills the process, so
 *      this `object` reinitializes from scratch. `AutoQueueManager.attach()` is called
 *      from `MusicService.onCreate()` in the SAME coroutine block, immediately before
 *      `controller.initialize()` and before settings/DataStore have necessarily been
 *      read even once — a classic cold-start ordering race. The old code only checked
 *      once, right there, and had no way to recover if that first check came too early
 *      to see anything.
 *
 * THE FIX is not a single guessed root cause — it's removing the SPOF (single point of
 * failure) in the trigger path entirely:
 *   - State now lives on a single `Queue` instance ([currentQueue]), not loose globals.
 *     Ported from Metrolist's `playback/queues/Queue.kt` family (see that package for
 *     full attribution/design notes).
 *   - A refill request that arrives while the mutex is busy is now remembered
 *     (`refillRequestedAgain`) and re-run immediately after, instead of being dropped.
 *   - The player listener now reacts to FOUR events instead of one:
 *       onMediaItemTransition, onTimelineChanged(PLAYLIST_CHANGED), onIsPlayingChanged,
 *       and onPlaybackStateChanged(STATE_ENDED). onTimelineChanged with a
 *       playlist-changed reason in particular is the catch-all: it fires whenever the
 *       player's item list is set/replaced/extended by ANY code path — a fresh seed(),
 *       a restored session, anything — not just when the *current* item changes.
 *   - attach() performs an immediate check AND schedules exactly one deferred safety
 *     check ~2.5s later, specifically to close the cold-start race window without
 *     resorting to a forever-polling watchdog (which the old version had running every
 *     20s for the lifetime of the service for no benefit beyond this one race).
 *   - Reading `settings.autoQueueEnabled` is now wrapped in a short timeout with a safe
 *     default, so a slow first DataStore read on a cold process can't make the whole
 *     check silently no-op.
 *
 * Public API (function names/signatures) is intentionally unchanged — it's called from
 * ~15 sites across PlayerViewModel/SettingsViewModel/ListeningStatsTracker/MusicService,
 * none of which needed to change for this rewrite.
 */
object AutoQueueManager {
    private const val TAG = "AutoQueueMgr"

    // Fixed, small threshold — mirrors Metrolist's MusicService trigger
    // (`mediaItemCount - currentMediaItemIndex <= 5`). The old adaptive 25–47 range
    // added complexity without a measurable benefit, and was itself a source of
    // instability: it changed the meaning of "queue full" based on recent skip
    // velocity, so two consecutive checks could disagree about whether a refill was
    // even needed.
    private const val REFILL_THRESHOLD = 8

    // One-shot safety check after attach(), to catch the cold-start race with
    // controller.initialize() / a slow first DataStore read — NOT a recurring poll.
    private const val STARTUP_SAFETY_CHECK_DELAY_MS = 2_500L

    private const val SETTINGS_READ_TIMEOUT_MS = 3_000L

    // --- State: one Queue instance, not a bag of loose global vars ---
    @Volatile private var currentQueue: Queue = EmptyQueue

    // Refill mutex — only one refill runs at a time. Unlike the old version, a request
    // that arrives while busy is remembered and re-run immediately after, not dropped.
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

    // --- Player Listener: four independent triggers, not one ---
    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null) return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
            printd("$TAG: onMediaItemTransition reason=$reason mediaId=${mediaItem.mediaId}")
            checkAndRefill()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Catch-all: fires whenever the player's item list is set/replaced/grown by
            // ANY code path, regardless of whether the *current* item changed. This is
            // what makes a restored/externally-populated queue self-heal without
            // needing to match onMediaItemTransition's narrower firing conditions.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                printd("$TAG: onTimelineChanged(PLAYLIST_CHANGED), windowCount=${timeline.windowCount}")
                checkAndRefill()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                printd("$TAG: STATE_ENDED — forcing immediate refill")
                checkAndRefill(forceImmediate = true)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Safety net for seeks near the end of the queue and for resuming a
            // just-restored session, without a 20s-forever polling loop.
            if (isPlaying) checkAndRefill()
        }
    }

    // ===== PUBLIC API (signatures unchanged — see PlayerViewModel / SettingsViewModel / MusicService) =====

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao,
        engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao,
        onQueueItemsAdded: (() -> Unit)? = null
    ) {
        scope = coroutineScope
        contextRef = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        musicDaoRef = musicDao
        engagementDaoRef = engagementDao
        onQueueItemsAddedCallback = onQueueItemsAdded
        player.addListener(playerListener)
        printd("$TAG: Attached to player")

        checkAndRefill()

        // Closes the cold-start race with MusicService.onCreate()'s
        // controller.initialize() (queue restoration) and with a slow first
        // DataStore read — see the class doc for the full explanation. This is a
        // single deferred check, not a recurring poll.
        getActiveScope().launch(Dispatchers.IO) {
            delay(STARTUP_SAFETY_CHECK_DELAY_MS)
            checkAndRefill()
        }
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
            printd("$TAG: Player updated")
            checkAndRefill()
        }
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        scope = null
        contextRef = null
        datastoreRepository = null
        musicDaoRef = null
        engagementDaoRef = null
        printd("$TAG: Detached")
    }

    fun reset() {
        printd("$TAG: reset() called")
        currentQueue = EmptyQueue
    }

    /**
     * Called when auto-queue is disabled (toggle ON->OFF). Trims upcoming tracks so a
     * subsequent re-enable starts clean, without touching anything at or before the
     * currently playing item.
     */
    fun disableAndTrimQueue() {
        reset()
        getActiveScope().launch(Dispatchers.Main) {
            val player = playerRef ?: return@launch
            val totalCount = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (totalCount <= currentIndex + 1) return@launch
            player.removeMediaItems(currentIndex + 1, totalCount)
            printd("$TAG: disableAndTrimQueue removed ${totalCount - currentIndex - 1} upcoming tracks")
        }
    }

    /**
     * Called when auto-queue is re-enabled (toggle OFF->ON). Builds a fresh
     * [ResilientRadioQueue] seeded from whatever's currently playing, then refills
     * immediately. This is the manual path that "always worked" — it's now also what
     * the automatic triggers above converge on, so it should no longer be necessary
     * to invoke it by hand to get things moving.
     */
    fun resetAndReseedFromCurrentSong() {
        printd("$TAG: resetAndReseedFromCurrentSong()")
        reset()
        val player = playerRef ?: return
        getActiveScope().launch(Dispatchers.IO) {
            try {
                if (!isAutoQueueEnabled()) {
                    printd("$TAG: AutoQueue disabled, skipping reseed")
                    return@launch
                }
                val currentId = withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId } ?: return@launch
                seedFromMediaId(currentId)
                checkAndRefill(forceImmediate = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                printe("$TAG: resetAndReseedFromCurrentSong failed: ${e.message}")
            }
        }
    }

    /**
     * Explicit external seed — called by PlayerViewModel right after it starts playing a
     * known YouTube song and already has a resolved [endpoint]/[continuation] from its
     * own initial `YouTube.next()` call. Resumes from that continuation directly rather
     * than re-fetching page 1 (which would duplicate what the caller already added).
     */
    fun seed(endpoint: WatchEndpoint, continuation: String?, videoId: String) {
        val dao = musicDaoRef ?: return
        currentQueue = ResilientRadioQueue.resumed(
            endpoint = endpoint,
            continuation = continuation,
            musicDao = dao,
            isOnline = ::hasInternet,
        )
        printd("$TAG: seed() videoId=$videoId hasContinuation=${continuation != null}")
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
        // Skip velocity no longer drives an adaptive threshold (see REFILL_THRESHOLD
        // doc above), but a skip still often warrants a proactive top-up since it burns
        // through the queue faster than steady playback.
        checkAndRefill()
    }

    /** Unrelated to auto-queue paging — kept for PlayerViewModel's initial-queue-build path. */
    suspend fun buildMixQueue(seedSong: Song, onlineRelated: List<Song>): List<Song> {
        return if (onlineRelated.isNotEmpty()) {
            (listOf(seedSong) + onlineRelated).distinctBy { it.youtubeId ?: it.id }
        } else {
            listOf(seedSong)
        }
    }

    // ===== CORE ENGINE =====

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
                printe("$TAG: checkAndRefill error: ${e.message}")
            }
        }
    }

    /**
     * Core refill: append one page from [currentQueue]. Guarded by [refillMutex] so
     * only one refill runs at a time — but a refill request that arrives while the
     * mutex is held is remembered and re-run immediately after, instead of silently
     * disappearing (this was the old version's biggest single reliability bug).
     */
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
            // Nothing seeded yet — e.g. a cold-started/restored session where seed()
            // was never explicitly called for this process. Seed from whatever's
            // currently playing before giving up.
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
            printe("$TAG: nextPage() failed: ${e.message}")
            return
        }

        val newItems = fetched.filterNot { it.mediaId in existingIds }
        if (newItems.isEmpty()) return

        withContext(Dispatchers.Main) {
            playerRef?.addMediaItems(newItems)
        }
        onQueueItemsAddedCallback?.invoke()
        printd("$TAG: Refill added ${newItems.size} items (remaining was $remaining)")
    }

    // ===== SEEDING =====

    private suspend fun seedFromMediaId(mediaId: String) {
        val dao = musicDaoRef ?: return
        val videoId = resolveVideoId(mediaId)
        if (videoId == null) {
            // No YouTube ID for this song (a purely local file, most likely) — go
            // straight to a local-only ResilientRadioQueue instead of failing.
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
        // Defaults to enabled if the DataStore read doesn't complete quickly — a slow
        // first cold read must never look identical to "auto-queue is off".
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

    /**
     * Accurate count of upcoming items from the current playback position. When
     * shuffle is active, walks the next-window chain in shuffle order rather than
     * assuming index order — kept from the previous implementation, since it correctly
     * handles a case Metrolist's own simpler `mediaItemCount - currentMediaItemIndex`
     * check does not.
     */
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
