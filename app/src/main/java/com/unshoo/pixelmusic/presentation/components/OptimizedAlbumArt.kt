package com.unshoo.pixelmusic.presentation.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Dimension
import coil.size.Size
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.utils.LocalArtworkUri

// Keep "original" album art bounded. Many embedded covers are 3000-6000px; decoding
// those into a 256 MB heap can OOM after long sessions. 1280px is visually sharp for
// the full player while using ~60% less memory than 2048px.
internal const val MaxSafeAlbumArtDimensionPx = 1280
internal val SafeOriginalAlbumArtSize = Size(MaxSafeAlbumArtDimensionPx, MaxSafeAlbumArtDimensionPx)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = SafeOriginalAlbumArtSize,
    placeholderModel: Any? = null
) {
    val context = LocalContext.current
    // `density` is deliberately no longer read here: targetSize is already in pixels.
    val requestTargetSize = remember(targetSize) {
        val safeSize = safeAlbumArtTargetSize(targetSize)
        val w = (safeSize.width as? Dimension.Pixels)?.px
        val h = (safeSize.height as? Dimension.Pixels)?.px
        if (w != null && h != null) {
            // PERF/BUGFIX: `targetSize` is already expressed in PIXELS by callers
            // (e.g. AlbumArtQuality.maxSize is 512px for Medium, 800px for High;
            // SafeOriginalAlbumArtSize is 1280px). Multiplying by density a second time
            // forced all sizes above LOW to clamp to 1280px, allocating 6.55MB ARGB_8888
            // bitmaps in ART Large Object Space and dropping HWUI image decodes.
            // Treating targetSize as pixels also re-aligns this request with
            // PrefetchAlbumNeighbors, making memory cache lookups hit as intended.
            val scaledW = w.coerceAtMost(MaxSafeAlbumArtDimensionPx)
            val scaledH = h.coerceAtMost(MaxSafeAlbumArtDimensionPx)
            Size(scaledW, scaledH)
        } else {
            safeSize
        }
    }
    val isStableLocalArtwork = remember(uri) {
        when (uri) {
            is String -> LocalArtworkUri.isLocalArtworkUri(uri)
            is Uri -> LocalArtworkUri.isLocalArtworkUri(uri)
            is ImageRequest -> {
                val data = uri.data
                (data as? String)?.let(LocalArtworkUri::isLocalArtworkUri) == true ||
                    LocalArtworkUri.isLocalArtworkUri(data as? Uri)
            }
            else -> false
        }
    }

    if (renderDirectAlbumArt(
            model = uri,
            title = title,
            modifier = modifier
        )
    ) {
        return
    }

    val memoryCacheKey = remember(uri, requestTargetSize) {
        albumArtMemoryCacheKey(uri, requestTargetSize)
    }
    val placeholderMemoryCacheKey = remember(memoryCacheKey, uri) {
        when (uri) {
            is ImageRequest -> uri.placeholderMemoryCacheKey
                ?: uri.memoryCacheKey
                ?: memoryCacheKey?.let { MemoryCache.Key(it) }
            else -> memoryCacheKey?.let { MemoryCache.Key(it) }
        }
    }
    val requestModel = remember(context, uri, requestTargetSize) {
        when (uri) {
            is ImageRequest -> uri.newBuilder(context).apply {
                size(requestTargetSize)
                if (uri.memoryCacheKey == null) {
                    memoryCacheKey(memoryCacheKey)
                }
                placeholderMemoryCacheKey(placeholderMemoryCacheKey)
            }.build()
            else -> ImageRequest.Builder(context)
                .data(uri)
                .crossfade(350) // Use Coil's native crossfade
                .error(R.drawable.ic_music_placeholder)
                .placeholder(R.drawable.ic_music_placeholder)
                .size(requestTargetSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(if (isStableLocalArtwork) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .apply {
                    if (memoryCacheKey != null) {
                        memoryCacheKey(memoryCacheKey)
                    }
                    if (placeholderMemoryCacheKey != null) {
                        placeholderMemoryCacheKey(placeholderMemoryCacheKey)
                    }
                }
                .build()
        }
    }

    AsyncImage(
        model = requestModel,
        contentDescription = "Album art of $title",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_music_placeholder),
        error = painterResource(R.drawable.ic_music_placeholder)
    )
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_music_placeholder),
            contentDescription = "$title placeholder",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant
            ),
        )
    }
}

@Composable
private fun renderDirectAlbumArt(
    model: Any?,
    title: String,
    modifier: Modifier
): Boolean {
    return when (model) {
        is ImageRequest -> renderDirectAlbumArt(model.data, title, modifier)
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Painter -> {
            Image(
                painter = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is ImageBitmap -> {
            Image(
                bitmap = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Bitmap -> {
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        else -> false
    }
}

internal fun safeAlbumArtTargetSize(targetSize: Size): Size {
    return if (targetSize == Size.ORIGINAL) {
        SafeOriginalAlbumArtSize
    } else {
        targetSize
    }
}

internal fun albumArtMemoryCacheKey(model: Any?, targetSize: Size): String? {
    val data = when (model) {
        is ImageRequest -> model.data
        else -> model
    } ?: return null

    val baseKey = when (data) {
        is String -> data.takeIf { it.isNotBlank() }
        is Uri -> data.toString().takeIf { it.isNotBlank() }
        else -> null
    } ?: return null

    if (targetSize == Size.ORIGINAL) return baseKey

    val width = (targetSize.width as? Dimension.Pixels)?.px
    val height = (targetSize.height as? Dimension.Pixels)?.px
    return if (width != null && height != null) {
        "${baseKey}_${width}x${height}"
    } else {
        "${baseKey}_${targetSize.width}x${targetSize.height}"
    }
}

