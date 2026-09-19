package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
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
import timber.log.Timber
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

            val totalSongs = playlist.songs.size
            if (totalSongs == 0) return@withContext Result.success()

            val processedSongs = AtomicInteger(0)
            val succeededSongs = AtomicInteger(0)
            val semaphore = Semaphore(Constants.Downloads.MAX_CONCURRENT_DOWNLOADS)

            try {
                PixelMusicNotificationManager.showPlaylistDownloadProgress(
                    appContext,
                    playlist,
                    0,
                    totalSongs
                )
                setProgress(workDataOf(PROGRESS_CURRENT to 0, PROGRESS_TOTAL to totalSongs))

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
                val localSongs = musicDao.getSongsBySourceType(0).filter { it.filePath.isNotBlank() && File(it.filePath).length() > 0L }

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

                                val localMatching = com.unshoo.pixelmusic.utils.LocalAudioDuplicateMatcher.findMatchingLocalSong(
                                    localSongs = localSongs,
                                    song = song
                                )

                                val isExistingLocal = localMatching != null
                                val audioPath = if (localMatching != null) {
                                    Timber.i("Song '${song.title}' already exists in local storage at ${localMatching.filePath}. Skipping network download.")
                                    localMatching.filePath
                                } else {
                                    DownloadHelper.downloadAudio(appContext, song)
                                }

                                val thumbnailPath =
                                    DownloadHelper.downloadImage(
                                        appContext,
                                        fullSong?.thumbnailHref ?: song.thumbnailHref,
                                        song.youtubeId
                                    )

                                val updatedSong = song.copy(
                                    thumbnailPath = thumbnailPath?.path ?: localMatching?.albumArtUriString,
                                    audioFilePath = audioPath,
                                    album = fullSong?.album ?: song.album ?: localMatching?.albumName,
                                    albumBrowseId = fullSong?.albumBrowseId ?: song.albumBrowseId,
                                )

                                localSongRepository.create(updatedSong)

                                if (audioPath != null && !isExistingLocal) {
                                    embedAudioMetadata(
                                        audioPath = audioPath,
                                        title = updatedSong.title,
                                        artist = updatedSong.artist,
                                        album = updatedSong.album?.takeIf { it.isNotBlank() } ?: "YouTube Music",
                                        thumbnailFile = thumbnailPath
                                    )
                                }

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
                                setProgress(
                                    workDataOf(
                                        PROGRESS_CURRENT to processed,
                                        PROGRESS_TOTAL to totalSongs
                                    )
                                )

                            } catch (_: CancellationException) {
                                PixelMusicHelper.printd("Playlist song download canceled ${song.title}")
                            } catch (e: Exception) {
                                PixelMusicHelper.printd("Playlist song download failed: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                }.awaitAll()

                if (succeededSongs.get() > 0) {
                    PixelMusicNotificationManager.showPlaylistDownloadSuccess(
                        appContext,
                        playlist
                    )
                    PixelMusicHelper.printd("Playlist download complete")
                    Result.success()
                } else {
                    PixelMusicNotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                    PixelMusicHelper.printd("Playlist download finished with failures")
                    Result.failure()
                }
            } catch (_: CancellationException) {
                PixelMusicNotificationManager.showPlaylistDownloadCanceled(appContext, playlist)
                PixelMusicHelper.printd("Playlist download canceled ${playlist.info.title}")
                Result.failure()
            } catch (e: Exception) {
                PixelMusicHelper.printd("Playlist download failed: ${e.message}")
                e.printStackTrace()
                PixelMusicNotificationManager.showPlaylistDownloadFailure(appContext, playlist)
                Result.failure()
            }
        }
    }

    private fun embedAudioMetadata(
        audioPath: String,
        title: String,
        artist: String,
        album: String,
        thumbnailFile: File?
    ) {
        try {
            val pfd = if (audioPath.startsWith("content://")) {
                appContext.contentResolver.openFileDescriptor(android.net.Uri.parse(audioPath), "rw")
            } else {
                val f = File(audioPath)
                if (!f.exists()) return
                ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_WRITE)
            } ?: return

            pfd.use { fd ->
                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val propertyMap = HashMap(metadata?.propertyMap ?: emptyMap())
                if (title.isNotBlank()) propertyMap["TITLE"] = arrayOf(title)
                if (artist.isNotBlank()) propertyMap["ARTIST"] = arrayOf(artist)
                if (album.isNotBlank()) propertyMap["ALBUM"] = arrayOf(album)

                TagLib.savePropertyMap(fd.dup().detachFd(), propertyMap)

                if (thumbnailFile != null && thumbnailFile.exists()) {
                    val bytes = thumbnailFile.readBytes()
                    if (bytes.isNotEmpty()) {
                        val pic = Picture(
                            data = bytes,
                            description = "Front Cover",
                            pictureType = "Front Cover",
                            mimeType = "image/jpeg"
                        )
                        TagLib.savePictures(fd.dup().detachFd(), arrayOf(pic))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("PlaylistDownloadWorker").w(e, "Failed to embed TagLib metadata for %s", audioPath)
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
        val title = song.title.ifBlank { "Downloaded Song" }
        val artist = song.artist.ifBlank { "Unknown Artist" }
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

        val albumName = song.album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
        val albumId = toUnifiedYoutubeAlbumId(albumName)
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
            sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE,
            albumBrowseId = song.albumBrowseId
        )

        val existing = musicDao.getSongByIdOnce(songId)
        if (existing != null && existing.albumName == "YouTube Music" && albumName != "YouTube Music") {
            musicDao.insertAlbumsIgnoreConflicts(listOf(albumToInsert))
            musicDao.updateSongAlbum(songId, albumName, albumId, song.albumBrowseId)
        }

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
