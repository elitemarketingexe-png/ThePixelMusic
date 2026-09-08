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

class ResilientRadioQueue(
    private var online: YouTubeRadioQueue?,
    private val musicDao: MusicDao,
    private val isOnline: () -> Boolean,
    private var localSeed: SongEntity? = null,
) : Queue {

    private var onlineExhausted = online == null
    private var lastKnownVideoId: String? = online?.currentVideoId
    private var hasAttemptedReseedAfterFailure = false
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
            }
        }
        onlineExhausted = true
        return Queue.Status(title = null, items = emptyList(), mediaItemIndex = -1)
    }

    override fun hasNextPage(): Boolean = true

    override suspend fun nextPage(): List<MediaItem> {
        if (!onlineExhausted && online != null && isOnline() && online?.hasNextPage() == true) {
            try {
                val items = online!!.nextPage()
                online?.currentVideoId?.let { lastKnownVideoId = it }
                if (items.isNotEmpty()) {
                    hasAttemptedReseedAfterFailure = false
                    return items
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        if (online == null || online?.hasNextPage() == false) {
            val seedId = lastKnownVideoId
            if (isOnline() && seedId != null && !hasAttemptedReseedAfterFailure) {
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
                }
            }
            onlineExhausted = true
        }
        return fetchLocalFallback()
    }

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
