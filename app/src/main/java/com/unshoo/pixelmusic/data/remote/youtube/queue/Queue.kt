package com.unshoo.pixelmusic.data.remote.youtube.queue

import androidx.media3.common.MediaItem

interface Queue {
    /**
     * When non-null, the caller may hand this to ExoPlayer immediately so playback can
     * start before the rest of the queue has finished resolving over the network.
     * Most PixelMusic call sites already have a MediaItem playing by the time a Queue
     * is created, so this is usually null — it exists for API parity with Metrolist and
     * for future direct-queue playback paths.
     */
    val preloadItem: MediaItem?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    )
}
