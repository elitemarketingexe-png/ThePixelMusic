package com.unshoo.pixelmusic.data.remote.youtube.queue

import androidx.media3.common.MediaItem
object EmptyQueue : Queue {
    override val preloadItem: MediaItem? = null

    override suspend fun getInitialStatus() = Queue.Status(
        title = null,
        items = emptyList(),
        mediaItemIndex = -1,
    )

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaItem> = emptyList()
}
