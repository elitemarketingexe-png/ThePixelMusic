package com.unshoo.pixelmusic.data.remote.youtube.queue

import androidx.media3.common.MediaItem
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint

/**
 * YouTubeRadioQueue — ported from Metrolist's `playback/queues/YouTubeQueue.kt` and
 * adapted to PixelMusic's innertube client (`unshoo.ianshulyadav.pixelmusic.innertube`)
 * and Song/MediaItem conversion (`toNativeSong()` + `MediaItemBuilder.build()`).
 *
 * Everything this queue needs to keep paging forever — the current [endpoint] and the
 * continuation [token] — lives as a private var on THIS instance. `AutoQueueManager`
 * never sees or touches them; it only calls [hasNextPage] / [nextPage].
 */
class YouTubeRadioQueue(
    private var endpoint: WatchEndpoint,
    initialContinuation: String? = null,
    override val preloadItem: MediaItem? = null,
) : Queue {

    // Lets a caller that already fetched page 1 itself (see AutoQueueManager.seed())
    // hand us its continuation token directly, so our first nextPage() resumes from
    // there instead of re-fetching (and re-adding duplicates of) page 1.
    private var continuation: String? = initialContinuation
    private var retryCount = 0

    // Tracks whether this queue has genuinely run out of ways to continue — NOT the
    // same thing as `continuation == null`. `continuation` also goes null for one
    // call cycle whenever nextPage() reseeds a fresh radio mix from the last fetched
    // song (see nextPage() below); that's a normal mid-flight state, not exhaustion.
    // Conflating the two was a real bug: hasNextPage() would report false right after
    // a perfectly healthy reseed, causing ResilientRadioQueue to permanently give up
    // on the online radio for the rest of the session — which looked like the queue
    // "randomly" stopping, since exactly when a mix's continuation runs dry varies.
    private var exhausted = false

    /** The video this queue is currently anchored to — lets a caller reseed from the same point if this instance is ever discarded after a genuine failure. */
    val currentVideoId: String? get() = endpoint.videoId

    /**
     * Populated whenever a `next()` response includes a "related" browse endpoint.
     * [com.unshoo.pixelmusic.data.remote.youtube.queue.ResilientRadioQueue] uses this
     * as one more thing to try before giving up and falling back to the local library.
     */
    var relatedEndpoint: BrowseEndpoint? = null
        private set

    private class EmptyRadioQueueException : IllegalStateException()

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        var lastException: Throwable? = null

        // A bare videoId with no playlist means "start a fresh radio from this song" —
        // explicitly request YouTube's auto-generated mix playlist (RDAMVM) for it.
        if (endpoint.videoId != null && endpoint.playlistId == null) {
            endpoint = endpoint.copy(playlistId = "RDAMVM${endpoint.videoId}")
        }

        val isRadioRequest =
            endpoint.playlistId?.startsWith("RDAMVM") == true ||
                (endpoint.videoId != null && endpoint.playlistId == null)

        for (attempt in 0..MAX_RETRIES) {
            try {
                val nextResult = YouTube.next(endpoint, continuation).getOrThrow()

                var items = nextResult.items
                relatedEndpoint = nextResult.relatedEndpoint

                if (isRadioRequest && continuation == null && items.size <= 1) {
                    if (endpoint.playlistId?.startsWith("RDAMVM") == true) {
                        throw EmptyRadioQueueException()
                    } else if (nextResult.relatedEndpoint != null) {
                        val relatedPage = YouTube.related(nextResult.relatedEndpoint).getOrNull()
                        if (relatedPage != null && relatedPage.songs.isNotEmpty()) {
                            items = items + relatedPage.songs.filter { it.id != endpoint.videoId }
                        }
                    }
                }

                endpoint = nextResult.endpoint
                continuation = nextResult.continuation
                retryCount = 0
                return@withContext Queue.Status(
                    title = nextResult.title,
                    items = items.map { MediaItemBuilder.build(it.toNativeSong()) },
                    mediaItemIndex = nextResult.currentIndex ?: 0,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (e is EmptyRadioQueueException &&
                    endpoint.playlistId?.startsWith("RDAMVM") == true &&
                    endpoint.videoId != null
                ) {
                    // The mix playlist for this exact video came back empty. Retry with
                    // just the bare videoId so YouTube builds a fresh mix around it
                    // instead of failing outright.
                    endpoint = WatchEndpoint(videoId = endpoint.videoId)
                } else if (attempt < MAX_RETRIES) {
                    delay(500L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IllegalStateException("Failed to get initial radio status")
    }

    // Only reports false once we've genuinely run out of options — a repeated failure
    // after retries, or a page that came back with no continuation AND no items. A
    // mid-reseed `continuation == null` (see nextPage()) does NOT count as exhausted.
    override fun hasNextPage(): Boolean = !exhausted

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        var lastException: Throwable? = null

        for (attempt in 0..MAX_RETRIES) {
            try {
                val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
                endpoint = nextResult.endpoint
                continuation = nextResult.continuation
                if (nextResult.relatedEndpoint != null) relatedEndpoint = nextResult.relatedEndpoint
                retryCount = 0

                val items = nextResult.items.map { MediaItemBuilder.build(it.toNativeSong()) }

                if (continuation == null) {
                    if (items.isNotEmpty()) {
                        // The radio's continuation ran dry (YouTube caps how far a
                        // single mix continues), but we DO have somewhere to go next:
                        // reseed a brand-new radio mix from the last song we just
                        // fetched. This is the crux of "seed infinitely" — every
                        // exhausted continuation immediately becomes the seed for the
                        // next one. We are NOT exhausted, so leave that flag false;
                        // the next nextPage() call will fetch this new mix's page 1.
                        val lastId = nextResult.items.last().id
                        endpoint = WatchEndpoint(videoId = lastId, playlistId = "RDAMVM$lastId")
                    } else {
                        // No continuation AND nothing came back at all — genuinely
                        // nothing left to fetch from this line of radio.
                        exhausted = true
                    }
                }

                return@withContext items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                retryCount++
                if (retryCount >= MAX_RETRIES) {
                    // Give up on this continuation rather than retrying a dead token
                    // forever. hasNextPage() now correctly reports false, so
                    // ResilientRadioQueue knows to try reseeding from the last known
                    // video (see ResilientRadioQueue) before falling back to local.
                    exhausted = true
                    continuation = null
                } else {
                    delay(500L * retryCount)
                }
            }
        }
        throw lastException ?: IllegalStateException("Failed to get next radio page")
    }

    companion object {
        private const val MAX_RETRIES = 3

        /** Starts a fresh radio mix seeded from [videoId]. */
        fun radio(videoId: String, preloadItem: MediaItem? = null): YouTubeRadioQueue =
            YouTubeRadioQueue(
                endpoint = WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId"),
                preloadItem = preloadItem,
            )
    }
}
