package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
object QueuePreloadManager {

    private var preloadJob: Job? = null
    private var scope: CoroutineScope? = null
    private var appContext: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var exoCache: ExoCache? = null
    private var engineRef: DualPlayerEngine? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            triggerPreload(includeCurrent = false)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                triggerPreload(includeCurrent = true)
            }
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        exoCacheInstance: ExoCache,
        engine: DualPlayerEngine? = null
    ) {
        scope = coroutineScope
        appContext = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        exoCache = exoCacheInstance
        engineRef = engine
        player.addListener(playerListener)
    }

    fun detach(player: Player?) {
        playerRef?.removeListener(playerListener)
        player?.removeListener(playerListener)
        playerRef = null
        preloadJob?.cancel()
        scope = null
        appContext = null
        datastoreRepository = null
        exoCache = null
        engineRef = null
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
        }
    }

    fun onControllerReady(player: Player) {
        updatePlayer(player)
    }

    private fun triggerPreload(includeCurrent: Boolean = false) {
        val currentScope = scope ?: return
        val player = playerRef ?: return
        val ctx = appContext ?: return

        preloadJob?.cancel()
        preloadJob = currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.preloadQueueEnabled) return@launch

            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else Pair(player.currentMediaItemIndex, player.mediaItemCount)
            } ?: return@launch

            val (currentIndex, totalCount) = playerState

            if (totalCount <= 0 || currentIndex < 0) return@launch
            val startIndex = if (includeCurrent) currentIndex else currentIndex + 1
            val endIndex = (currentIndex + settings.preloadQueueSize).coerceAtMost(totalCount - 1)
            if (startIndex > endIndex) return@launch
            val indicesAhead = startIndex..endIndex

            for (i in indicesAhead) {
                val mediaItem = withContext(Dispatchers.Main) {
                    if (playerRef != null && i < player.mediaItemCount) player.getMediaItemAt(i) else null
                } ?: continue

                val videoId = resolveYoutubeVideoId(mediaItem) ?: continue

                val song = Song(
                    youtubeId = videoId,
                    title = mediaItem.mediaMetadata.title?.toString() ?: "",
                    artist = mediaItem.mediaMetadata.artist?.toString() ?: "",
                    album = mediaItem.mediaMetadata.albumTitle?.toString(),
                    thumbnailHref = upgradeThumbnailUrlToHighQuality(mediaItem.mediaMetadata.artworkUri?.toString()).orEmpty()
                )

                var streamUrl: String? = null
                try {
                    // Prefetch at the user's selected quality (HIGH → highest stream first).
                    streamUrl = YoutubeHelper.getSongPlayerUrl(ctx, song, allowLocal = false)
                    if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http")) {
                        val ytUri = "youtube://$videoId"
                        engineRef?.resolvedUriCache?.put(ytUri, android.net.Uri.parse(streamUrl))
                        engineRef?.preCacheFirstChunk(streamUrl)
                    }
                } catch (_: Exception) {
                }

                if (!streamUrl.isNullOrBlank() && streamUrl.startsWith("http") && i <= currentIndex + 1) {
                    // Smaller first-chunk on preload; DualPlayerEngine also pre-caches on resolve.
                    prefetchAudioBytes(ctx, videoId, streamUrl)
                }

                val thumbnailUrl = song.thumbnailHref
                if (thumbnailUrl.isNotBlank()) {
                    try {
                        val imageDir = PixelMusicHelper.getDownloadDirectory(
                            ctx,
                            Constants.Downloads.THUMBNAILS_FOLDER
                        )
                        val destFile = File(imageDir, "$videoId.jpg")
                        if (!destFile.exists()) {
                            val artBytes = PixelMusicHelper.fetchArtworkBytes(thumbnailUrl)
                            if (artBytes != null && artBytes.isNotEmpty()) {
                                destFile.writeBytes(artBytes)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                delay(if (includeCurrent && i == currentIndex) 120 else 350)
            }
        }
    }

    private fun resolveYoutubeVideoId(mediaItem: MediaItem): String? {
        val mediaId = mediaItem.mediaId
        if (mediaId.startsWith("youtube_")) return mediaId.removePrefix("youtube_").takeIf { it.isNotBlank() }
        mediaItem.localConfiguration?.uri?.toString()
            ?.takeIf { it.startsWith("youtube://") }
            ?.substringAfter("youtube://")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        mediaItem.mediaMetadata.extras
            ?.getString(com.unshoo.pixelmusic.utils.MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?.takeIf { it.startsWith("youtube://") }
            ?.substringAfter("youtube://")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return mediaId.takeIf { it.isNotBlank() && !it.startsWith("-") }
    }

    private suspend fun prefetchAudioBytes(ctx: Context, videoId: String, streamUrl: String) {
        val cache = exoCache?.cache ?: return
        try {
            val uri = Uri.parse(streamUrl)
            val baseDataSourceFactory = DefaultDataSource.Factory(ctx)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(baseDataSourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            val dataSource = cacheDataSourceFactory.createDataSource()
            val dataSpec = DataSpec.Builder()
                .setUri(uri)
                .setPosition(0)
                .setLength(256 * 1024)
                .build()

            val parentJob = kotlin.coroutines.coroutineContext[Job]
            val progressListener = CacheWriter.ProgressListener { _, _, _ ->
                if (parentJob != null && !parentJob.isActive) {
                    throw InterruptedException("Prefetch canceled")
                }
            }

            val cacheWriter = CacheWriter(
                dataSource,
                dataSpec,
                null,
                progressListener
            )

            withContext(Dispatchers.IO) {
                cacheWriter.cache()
            }
        } catch (_: Exception) {
        }
    }
}
