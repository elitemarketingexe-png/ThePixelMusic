package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.size.Size
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.albumArtMemoryCacheKey
import com.unshoo.pixelmusic.presentation.components.safeAlbumArtTargetSize
import com.unshoo.pixelmusic.utils.LocalArtworkUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged


@Composable
fun PrefetchAlbumNeighbors(
    isActive: Boolean,
    pagerState: PagerState,
    queue: ImmutableList<Song>,
    radius: Int = 1,
    targetSize: Size = Size(600, 600),
    anchorIndex: Int? = null
) {
    if (!isActive || queue.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }

    LaunchedEffect(pagerState, queue, anchorIndex, requestTargetSize) {
        snapshotFlow { 
            // If the user is manually scrolling, follow the PagerState.
            // If the Pager is idle, prioritize the provided anchorIndex (which is tied to the current song)
            // to avoid fetching neighbors of a stale index after a queue shift.
            if (pagerState.isScrollInProgress) pagerState.currentPage 
            else anchorIndex ?: pagerState.currentPage 
        }
            .distinctUntilChanged()
            .collect { page ->
                val indices = (page - radius..page + radius)
                    .filter { it in queue.indices && it != page }
                indices.forEach { idx ->
                    queue[idx].albumArtUriString?.let { uri ->
                        val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) coil.request.CachePolicy.DISABLED else coil.request.CachePolicy.ENABLED
                        val memoryCacheKey = albumArtMemoryCacheKey(uri, requestTargetSize)
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .size(requestTargetSize)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .apply {
                                if (memoryCacheKey != null) {
                                    memoryCacheKey(memoryCacheKey)
                                }
                            }
                            .diskCachePolicy(diskPolicy)
                            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                        imageLoader.enqueue(req) // fire-and-forget
                    }
                }
            }
    }
}
