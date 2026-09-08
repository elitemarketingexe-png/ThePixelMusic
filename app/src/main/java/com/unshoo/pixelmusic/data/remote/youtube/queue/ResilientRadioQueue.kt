package com.unshoo.pixelmusic.data.remote.youtube.queue

import androidx.media3.common.MediaItem
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint

/**
 * ResilientRadioQueue — PixelMusic-specific decorator around [YouTubeRadioQueue] that
 * adds a local-library fallback. This is the one deliberate difference from Metrolist,
 * which has no local library to fall back to: PixelMusic can, and the old
 * `AutoQueueManager` already had this idea — it just implemented it as tangled global
 * state. Here it's a plain decorator: try online, fall back to local, never leave the
 * player with nothing.
 *
 * All state (which local songs have already been used, whether the online side is
 * exhausted) lives on this instance. A new radio session is a new instance — nothing
 * to reset, nothing to race.
 */
class ResilientRadioQueue(
    private var online: YouTubeRadioQueue?,
    private val musicDao: MusicDao,
    private val isOnline: () -> Boolean,
    private var localSeed: SongEntity? = null,
) : Queue {

    private var onlineExhausted = online == null
    private var lastKnownVideoId: String? = online?.currentVideoId

    // Genuine online failures (network exhausted after internal retries) get ONE
    // fresh reseed attempt from the last video we know before we give up on online
    // for the rest of the session. Without this, a single bad network blip early on
    // would strand the whole session on local-only fallback — which, for anyone with
    // a small or empty local library, looks identical to "the queue stopped
    // populating."
    private var hasAttemptedReseedAfterFailure = false

    // Local-library dedup, scoped to THIS queue instance's lifetime only — never a
    // process-wide blacklist, so a fresh radio session always starts with a clean slate.
    private val usedLocalIds = mutableSetOf<Long>()

    override val preloadItem: MediaItem? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val onlineQueue = online
        if (onlineQueue != null && isOnline()) {
            try {
                val status = onlineQueue.getInitialStatus()
                onlineQueue.currentVideoId?.let { lastKnownVideoId = it }
                if (status.items.isNotEmpty()) return status
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Network hiccup, expired token, whatever — fall through to local.
            }
        }
        onlineExhausted = true
        // Returning an empty initial status (rather than throwing) is intentional:
        // AutoQueueManager treats "empty + hasNextPage()==true" as "call nextPage()
        // again shortly", which immediately pulls from the local fallback below.
        return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
    }

    // There is always "more" available: either the online radio still has a
    // continuation, or the local library can supply more (and recycles once
    // exhausted). This is what makes the queue genuinely infinite rather than
    // eventually reporting "no more pages" and stalling out.
    override fun hasNextPage(): Boolean = true

    override suspend fun nextPage(): List<MediaItem> {
        if (!onlineExhausted && online != null && isOnline() && online?.hasNextPage() == true) {
            try {
                val items = online!!.nextPage()
                online?.currentVideoId?.let { lastKnownVideoId = it }
                if (items.isNotEmpty()) {
                    hasAttemptedReseedAfterFailure = false // a healthy fetch resets the one-shot reseed budget
                    return items
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fall through below; the genuine-exhaustion branch decides whether
                // to reseed or give up, rather than silently retrying forever here.
            }
        }

        if (online == null || online?.hasNextPage() == false) {
            val seedId = lastKnownVideoId
            if (isOnline() && seedId != null && !hasAttemptedReseedAfterFailure) {
                // Genuinely exhausted (or failed after internal retries) — try ONE
                // fresh radio seeded from the last video we know, instead of
                // immediately and permanently switching to local-only.
                hasAttemptedReseedAfterFailure = true
                online = YouTubeRadioQueue.radio(seedId)
                try {
                    val items = online!!.nextPage()
                    if (items.isNotEmpty()) {
                        online?.currentVideoId?.let { lastKnownVideoId = it }
                        return items
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Reseed itself failed — genuinely fall back to local now.
                }
            }
            onlineExhausted = true
        }
        return fetchLocalFallback()
    }

    /** Lets the caller supply (or refresh) the local DB entity for the seed song once resolved, so local-fallback candidates can branch out from it. */
    fun updateLocalSeed(entity: SongEntity?) {
        if (entity != null) localSeed = entity
    }

    private suspend fun fetchLocalFallback(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val candidates = mutableListOf<SongEntity>()
            val seed = localSeed

            if (seed != null) {
                runCatching {
                    candidates += musicDao.getLocalRelatedSongs(
                        songId = seed.id,
                        artistId = seed.artistId,
                        albumId = seed.albumId,
                        genre = seed.genre,
                        limit = 40,
                    )
                }
                if (seed.artistName.isNotBlank()) {
                    runCatching { candidates += musicDao.getSongsByArtistName(seed.artistName, 20) }
                }
                if (!seed.genre.isNullOrBlank() && !seed.genre.equals("YouTube", ignoreCase = true)) {
                    runCatching { candidates += musicDao.getSongsByGenre(seed.genre, seed.id, 20) }
                }
            }
            if (candidates.isEmpty()) {
                runCatching { candidates += musicDao.getRandomSongs(limit = 30) }
            }

            var fresh = candidates.distinctBy { it.id }.filter { it.id !in usedLocalIds }
            if (fresh.isEmpty() && candidates.isNotEmpty()) {
                // Cycled through everything we could find for this seed — recycle
                // rather than ever stalling. Real "infinite" for a finite local
                // library necessarily means repeating eventually.
                usedLocalIds.clear()
                fresh = candidates.distinctBy { it.id }
            }

            val picked = fresh.shuffled().take(15)
            picked.forEach { usedLocalIds += it.id }
            picked.map { MediaItemBuilder.build(it.toSong()) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Starts a fresh radio session seeded from [videoId] (or local-only if null). */
        fun radio(
            videoId: String?,
            musicDao: MusicDao,
            isOnline: () -> Boolean,
            localSeed: SongEntity? = null,
        ): ResilientRadioQueue = ResilientRadioQueue(
            online = videoId?.let { YouTubeRadioQueue.radio(it) },
            musicDao = musicDao,
            isOnline = isOnline,
            localSeed = localSeed,
        )

        /** Resumes a radio session from an endpoint/continuation the caller already fetched (avoids re-fetching page 1). */
        fun resumed(
            endpoint: WatchEndpoint,
            continuation: String?,
            musicDao: MusicDao,
            isOnline: () -> Boolean,
            localSeed: SongEntity? = null,
        ): ResilientRadioQueue = ResilientRadioQueue(
            online = YouTubeRadioQueue(endpoint = endpoint, initialContinuation = continuation),
            musicDao = musicDao,
            isOnline = isOnline,
            localSeed = localSeed,
        )
    }
}
