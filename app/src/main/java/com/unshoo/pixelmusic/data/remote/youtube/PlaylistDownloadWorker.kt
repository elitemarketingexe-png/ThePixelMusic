package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.absoluteValue
import com.unshoo.pixelmusic.utils.YouTubeIdUtils

class PlaylistDownloadWorker(
    private val appContext: Context,
    private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun musicDao(): com.unshoo.pixelmusic.data.database.MusicDao
    }

    private val playlistRepository = AppDatabase.getInstance(appContext).playlistRepository()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()
    private val songRepository = SongRepository()
    private val musicDao = EntryPointAccessors.fromApplication(
        appContext,
        WorkerEntryPoint::class.java
    ).musicDao()

    @OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val playlistId = params.inputData.getString(PLAYLIST_KEY)
                ?: return@withContext Result.failure()

            val playlist = playlistRepository.getPlaylistById(playlistId)
                ?: return@withContext Result.failure()

            try {
                val totalSongs = playlist.songs.size
                // Was a plain `var Int` incremented with `++downloadedSongs` from inside
                // concurrent `async` blocks running under a shared semaphore. Dispatchers.IO
                // is backed by a multi-thread pool, so concurrent non-atomic increments from
                // different songs could race and lose updates, leaving the progress
                // notification/UI stuck below the real count. AtomicInteger makes the
                // increment safe across threads.
                val processedSongs = AtomicInteger(0)
                val succeededSongs = AtomicInteger(0)

                PixelMusicNotificationManager.showPlaylistDownloadProgress(
                    appContext,
                    playlist,
                    0,
                    totalSongs
                )
                setProgress(workDataOf(PROGRESS_CURRENT to 0, PROGRESS_TOTAL to totalSongs))

                val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)
                val playlistImage =
                    DownloadHelper.downloadImage(
                        appContext,
                        playlist.info.coverHref,
                        playlist.info.id
                    )
                playlistRepository.insertPlaylist(
                    playlist.info.copy(
                        coverPath = playlistImage?.path
                    )
                )

                val currentDownloadedSize = playlistRepository.getPlaylistById(Constants.Downloads.DOWNLOADED_PLAYLIST_ID)?.songs?.size ?: 0

                playlist.songs.mapIndexed { index, song ->
                    async {
                        semaphore.withPermit {
                            try {
                                var fullSong: Song? = null
                                songRepository.getSongInfo(song.youtubeId)
                                    .collect { apiResult ->
                                        when (apiResult) {
                                            is ApiResult.Success -> {
                                                fullSong = apiResult.data
                                            }
                                            else -> {}
                                        }
                                    }

                                val audioPath =
                                    DownloadHelper.downloadAudio(appContext, song)
                                val thumbnailPath =
                                    DownloadHelper.downloadImage(
                                        appContext,
                                        fullSong?.thumbnailHref ?: song.thumbnailHref,
                                        song.youtubeId
                                    )

                                val updatedSong = song.copy(
                                    thumbnailPath = thumbnailPath?.path,
                                    audioFilePath = audioPath,
                                )

                                localSongRepository.create(updatedSong)

                                ensureYoutubeSongInLibrary(updatedSong)

                                if (audioPath != null) {
                                    val mainId = toUnifiedYoutubeSongId(song.youtubeId)
                                    val parentDir = File(audioPath).parentFile?.absolutePath ?: ""
                                    musicDao.updateSongFilePathAndParent(mainId, audioPath, parentDir)
                                    
                                    playlistRepository.insertCrossRef(
                                        PlaylistSongCrossRef(
                                            playlistId = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
                                            songId = song.youtubeId,
                                            position = currentDownloadedSize + index
                                        )
                                    )
                                    succeededSongs.incrementAndGet()
                                }

                                val processed = processedSongs.incrementAndGet()
                                PixelMusicNotificationManager.showPlaylistDownloadProgress(
                                    appContext,
                                    playlist,
                                    processed,
                                    totalSongs
                                )
                                setProgress(workDataOf(PROGRESS_CURRENT to processed, PROGRESS_TOTAL to totalSongs))

                            } catch (e: CancellationException) {
                                PixelMusicHelper.printd("Song download canceled ${song.title}")
                                throw e
                            } catch (e: Exception) {
                                PixelMusicHelper.printe(
                                    message = "Error downloading song: ${song.title}",
                                    exception = e
                                )
                                val processed = processedSongs.incrementAndGet()
                                PixelMusicNotificationManager.showPlaylistDownloadProgress(
                                    appContext,
                                    playlist,
                                    processed,
                                    totalSongs
                                )
                                setProgress(workDataOf(PROGRESS_CURRENT to processed, PROGRESS_TOTAL to totalSongs))
                            }
                        }
                    }
                }.awaitAll()

                if (succeededSongs.get() == totalSongs) {
                    PixelMusicNotificationManager.showPlaylistDownloadSuccess(appContext, playlist)
                    PixelMusicHelper.printd("Playlist download complete")
                    Result.success()
                } else {
                    // At least one song failed to download: don't claim full success, but the
                    // songs that did succeed are already saved, so this isn't a total failure
                    // either. Result.failure() (no automatic retry configured) with an accurate
                    // notification best reflects a partially-completed playlist download.
                    PixelMusicNotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                    PixelMusicHelper.printd(
                        "Playlist download finished with failures: ${succeededSongs.get()}/$totalSongs"
                    )
                    Result.failure()
                }
            } catch (e: CancellationException) {
                PixelMusicNotificationManager.showPlaylistDownloadCanceled(appContext, playlist)
                PixelMusicHelper.printd("Playlist download canceled ${playlist.info.title}")
                Result.failure()
            } catch (e: Exception) {
                PixelMusicNotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                PixelMusicHelper.printe(message = e.toString(), exception = e)
                Result.failure()
            }
        }
    }

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeSongId(youtubeId)

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumName)

    private fun toUnifiedYoutubeArtistId(artistName: String): Long =
        YouTubeIdUtils.toUnifiedYoutubeArtistId(artistName)

    private fun parseYoutubeArtistNames(artistStr: String): List<String> {
        if (artistStr.isBlank()) return listOf("Unknown Artist")
        val parsed = artistStr
            .split(Regex("\\s*[,/&;+、•]\\s*|\\s+(?:feat\\.|ft\\.|vs)\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private suspend fun ensureYoutubeSongInLibrary(song: Song) {
        val songId = toUnifiedYoutubeSongId(song.youtubeId)
        val title = song.title.takeIf { it.isNotBlank() } ?: "YouTube Video"
        val artist = song.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val artistNames = parseYoutubeArtistNames(artist)
        val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
        val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)

        val artistsToInsert = artistNames.map { name ->
            com.unshoo.pixelmusic.data.database.ArtistEntity(
                id = toUnifiedYoutubeArtistId(name),
                name = name,
                trackCount = 0,
                imageUrl = null
            )
        }

        val crossRefsToInsert = artistNames.mapIndexed { index, name ->
            val artistId = toUnifiedYoutubeArtistId(name)
            com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                songId = songId,
                artistId = artistId,
                isPrimary = index == 0
            )
        }

        val albumId = toUnifiedYoutubeAlbumId("YouTube Music")
        val albumName = "YouTube Music"
        val albumToInsert = com.unshoo.pixelmusic.data.database.AlbumEntity(
            id = albumId,
            title = albumName,
            artistName = primaryArtistName,
            artistId = primaryArtistId,
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = 0,
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref
        )

        val artistsJson = try {
            val arr = org.json.JSONArray()
            artistNames.forEachIndexed { idx, name ->
                val obj = org.json.JSONObject()
                obj.put("id", toUnifiedYoutubeArtistId(name))
                obj.put("name", name)
                obj.put("primary", idx == 0)
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            null
        }

        val durationMs = try {
            if (song.duration.contains(":")) {
                val parts = song.duration.split(":")
                when (parts.size) {
                    1 -> parts[0].toLong() * 1000L
                    2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
                    3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
                    else -> 0L
                }
            } else {
                song.duration.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }

        val songEntity = com.unshoo.pixelmusic.data.database.SongEntity(
            id = songId,
            title = title,
            artistName = artist,
            artistId = primaryArtistId,
            albumArtist = null,
            albumName = albumName,
            albumId = albumId,
            contentUriString = "youtube://${song.youtubeId}",
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref,
            duration = durationMs,
            genre = song.genre?.takeIf { it.isNotBlank() } ?: "YouTube Music",
            filePath = "",
            parentDirectoryPath = "youtube://",
            isFavorite = false,
            lyrics = null,
            trackNumber = 0,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            mimeType = "audio/webm",
            bitrate = null,
            sampleRate = null,
            telegramChatId = null,
            telegramFileId = null,
            artistsJson = artistsJson,
            sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE
        )

        musicDao.incrementalSyncMusicData(
            songs = listOf(songEntity),
            albums = listOf(albumToInsert),
            artists = artistsToInsert,
            crossRefs = crossRefsToInsert,
            deletedSongIds = emptyList()
        )
    }

    companion object {
        const val PLAYLIST_KEY = "playlist"
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
    }
}
