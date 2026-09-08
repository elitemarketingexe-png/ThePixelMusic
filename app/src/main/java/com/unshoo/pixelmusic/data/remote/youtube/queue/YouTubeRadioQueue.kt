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

class YouTubeRadioQueue(
    private var endpoint: WatchEndpoint,
    initialContinuation: String? = null,
    override val preloadItem: MediaItem? = null,
) : Queue {

    private var continuation: String? = initialContinuation
    private var retryCount = 0
    private var exhausted = false

    val currentVideoId: String? get() = endpoint.videoId

    var relatedEndpoint: BrowseEndpoint? = null
        private set

    private class EmptyRadioQueueException : IllegalStateException()

    override suspend fun getInitialStatus(): Queue.Status = withContext(IO) {
        var lastException: Throwable? = null

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
                    endpoint = WatchEndpoint(videoId = endpoint.videoId)
                } else if (attempt < MAX_RETRIES) {
                    delay(500L * (attempt + 1))
                }
            }
        }
        throw lastException ?: IllegalStateException("Failed to get initial radio status")
    }

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
                        val lastId = nextResult.items.last().id
                        endpoint = WatchEndpoint(videoId = lastId, playlistId = "RDAMVM$lastId")
                    } else {
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

        fun radio(videoId: String, preloadItem: MediaItem? = null): YouTubeRadioQueue =
            YouTubeRadioQueue(
                endpoint = WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId"),
                preloadItem = preloadItem,
            )
    }
}
