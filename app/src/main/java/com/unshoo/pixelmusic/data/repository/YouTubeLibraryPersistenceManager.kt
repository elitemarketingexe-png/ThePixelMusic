package com.unshoo.pixelmusic.data.repository

import android.content.Context
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import com.unshoo.pixelmusic.data.model.youtube.Song as YouTubeSong
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.YouTubeItemFilter
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class YouTubeLibraryPersistenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val favoritesDao: FavoritesDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {
    private val appDatabase by lazy { AppDatabase.getInstance(context) }
    private val playlistRepo by lazy { appDatabase.playlistRepository() }
    private val songRepo by lazy { appDatabase.songRepository() }

    /**
     * Atomically persists a YouTube playlist and its songs across Room SQLite databases:
     * 1. Validates with YouTubeItemFilter (rejects podcast/episode shelves).
     * 2. Writes PlaylistInfo into AppDatabase.
     * 3. Writes YouTube songs into AppDatabase.
     * 4. Writes ordered PlaylistSongCrossRefs (handles duplicate tracks via (playlistId, position)).
     * 5. Inserts converted native songs into unified MusicRepository (musicDao).
     * 6. Updates PlaylistPreferencesRepository.
     */
    suspend fun persistPlaylist(
        info: PlaylistInfo,
        songs: List<YouTubeSong>
    ): Boolean = withContext(Dispatchers.IO) {
        if (YouTubeItemFilter.isPodcastOrEpisode(info.title, info.id)) {
            Timber.d("YouTubeLibraryPersistenceManager: Skipping podcast playlist '%s' (%s)", info.title, info.id)
            return@withContext false
        }

        try {
            val highQualityCover = com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality(
                info.coverPath ?: info.coverHref
            ) ?: info.coverHref

            val updatedInfo = info.copy(
                coverHref = highQualityCover,
                lastSyncSongCount = songs.size,
                lastSyncTimestamp = System.currentTimeMillis()
            )

            val fullPlaylist = Playlist(
                info = updatedInfo,
                unsortedSongs = songs,
                crossRefs = songs.mapIndexed { index, song ->
                    PlaylistSongCrossRef(info.id, song.youtubeId, index)
                }
            )

            // 1. Write to AppDatabase (YouTube local tables)
            playlistRepo.insertPlaylistWithSongsPreserving(fullPlaylist, songRepo)

            // 2. Write to unified MusicRepository (MusicDao) for all app-wide flows
            val nativeSongs = songs.map { it.toNativeSong() }
            if (nativeSongs.isNotEmpty()) {
                musicRepository.insertYoutubeSongs(nativeSongs)
            }

            // 3. Update PlaylistPreferences
            val pId = info.id.removePrefix("VL")
            val (cTime, mTime) = playlistPreferencesRepository.getOrCreatePlaylistTimestamps(
                pId,
                updatedInfo.lastSyncTimestamp,
                updatedInfo.title,
                nativeSongs.map { it.id }
            )

            val prefPlaylist = com.unshoo.pixelmusic.data.model.Playlist(
                id = pId,
                name = updatedInfo.title,
                songIds = nativeSongs.map { it.id },
                createdAt = cTime,
                lastModified = mTime,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverImageUri = updatedInfo.coverHref,
                source = "YOUTUBE",
                displaySongCount = songs.size
            )
            playlistPreferencesRepository.updatePlaylist(prefPlaylist)

            Timber.i("YouTubeLibraryPersistenceManager: Successfully persisted playlist '%s' with %d songs", info.title, songs.size)
            true
        } catch (e: Exception) {
            Timber.e(e, "YouTubeLibraryPersistenceManager: Failed to persist playlist '%s'", info.title)
            false
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        try {
            val cleanId = playlistId.removePrefix("VL")
            playlistRepo.deleteFullPlaylist(playlistId)
            playlistRepo.deleteFullPlaylist("VL$cleanId")
            playlistRepo.deleteFullPlaylist(cleanId)
            playlistPreferencesRepository.deletePlaylist(cleanId)
            playlistPreferencesRepository.deletePlaylist(playlistId)
            Timber.i("YouTubeLibraryPersistenceManager: Deleted playlist %s", playlistId)
        } catch (e: Exception) {
            Timber.e(e, "YouTubeLibraryPersistenceManager: Failed to delete playlist %s", playlistId)
        }
    }

    suspend fun persistLikes(
        likedSongs: List<com.unshoo.pixelmusic.data.model.Song>,
        isFullSync: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (likedSongs.isEmpty()) return@withContext
        try {
            // 1. Insert into unified MusicRepository
            musicRepository.insertYoutubeSongs(likedSongs)

            // 2. Insert into favoritesDao
            val baseTimestamp = System.currentTimeMillis()
            val favoriteEntities = likedSongs.mapIndexedNotNull { index, song ->
                val songIdStr = song.youtubeId ?: song.id.removePrefix("youtube_").takeIf { song.id.startsWith("youtube_") }
                val numericId = song.id.toLongOrNull()
                    ?: songIdStr?.let { -(15_000_000_000_000L + abs(it.hashCode().toLong())) }
                    ?: return@mapIndexedNotNull null

                FavoritesEntity(
                    songId = numericId,
                    isFavorite = true,
                    timestamp = baseTimestamp - index
                )
            }

            if (isFullSync) {
                favoritesDao.replaceAll(favoriteEntities)
            } else {
                favoritesDao.insertAllBatched(favoriteEntities)
            }
            Timber.i("YouTubeLibraryPersistenceManager: Persisted %d liked songs (isFullSync=%b)", likedSongs.size, isFullSync)
        } catch (e: Exception) {
            Timber.e(e, "YouTubeLibraryPersistenceManager: Failed to persist liked songs")
        }
    }
}
