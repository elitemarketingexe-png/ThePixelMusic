package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.OfflineTrackDao
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.offline.CloudOfflineRepository
import com.unshoo.pixelmusic.data.offline.OfflineDownload
import com.unshoo.pixelmusic.data.offline.OfflineDownloadStatus
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.DownloadRepository
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicNotificationManager
import com.unshoo.pixelmusic.data.remote.youtube.PlaylistDownloadWorker
import com.unshoo.pixelmusic.data.remote.youtube.SongDownloadWorker
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudDownloadedSongItem(
    val song: Song,
    val download: OfflineDownload,
    val fileSizeBytes: Long
)

data class ActiveDownloadDisplayItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Float?, // null for indeterminate, 0f..1f for determinate
    val progressPercent: Int?,
    val isPlaylist: Boolean = false,
    val playlistId: String? = null,
    val sourceUri: String? = null,
    val workId: String? = null
)

enum class DownloadFilterMode {
    DOWNLOADED_ONLY,
    ALL_OFFLINE
}

data class CloudDownloadsUiState(
    val completedDownloads: List<CloudDownloadedSongItem> = emptyList(),
    val activeDownloads: List<ActiveDownloadDisplayItem> = emptyList(),
    val failedDownloads: List<OfflineDownload> = emptyList(),
    val storageUsedBytes: Long = 0L,
    val totalCompletedCount: Int = 0,
    val totalCount: Int = 0,
    val selectedSongIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isDownloadsPaused: Boolean = false,
    val isLoading: Boolean = true,
    val filterMode: DownloadFilterMode = DownloadFilterMode.DOWNLOADED_ONLY
)

@HiltViewModel
class CloudDownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CloudOfflineRepository,
    private val dao: OfflineTrackDao,
    private val musicDao: MusicDao
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val appDatabase = AppDatabase.getInstance(context)
    private val selectedSongIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isDownloadsPausedFlow = MutableStateFlow(false)
    private val filterModeFlow = MutableStateFlow(DownloadFilterMode.DOWNLOADED_ONLY)
    private var pausedActiveDownloads: List<ActiveDownloadDisplayItem> = emptyList()

    private val playlistWorksFlow = workManager.getWorkInfosByTagFlow(
        PlaylistDownloadWorker::class.java.name
    )
    private val songWorksFlow = workManager.getWorkInfosByTagFlow(
        SongDownloadWorker::class.java.name
    )
    private val localSongsFlow = appDatabase.songRepository().observeDownloadedSongs()
    private val cloudDownloadsFlow = repository.observeAll()

    fun toggleFilterMode() {
        filterModeFlow.update { if (it == DownloadFilterMode.DOWNLOADED_ONLY) DownloadFilterMode.ALL_OFFLINE else DownloadFilterMode.DOWNLOADED_ONLY }
    }

    private data class UiControlsState(
        val selectedIds: Set<String>,
        val isPaused: Boolean,
        val filterMode: DownloadFilterMode
    )

    private val uiControlsFlow = combine(
        selectedSongIdsFlow,
        isDownloadsPausedFlow,
        filterModeFlow
    ) { ids, paused, mode ->
        UiControlsState(ids, paused, mode)
    }

    val uiState: StateFlow<CloudDownloadsUiState> = combine(
        playlistWorksFlow,
        songWorksFlow,
        localSongsFlow,
        cloudDownloadsFlow,
        uiControlsFlow
    ) { playlistWorks, songWorks, localSongs, cloudDownloads, controls ->
        withContext(Dispatchers.IO) {
            val filterMode = controls.filterMode
            val selectedIds = controls.selectedIds
            val isPaused = controls.isPaused
            val activeItems = ArrayList<ActiveDownloadDisplayItem>()
            val failed = ArrayList<OfflineDownload>()
            val completedItems = ArrayList<CloudDownloadedSongItem>()

            // 1. Process active playlist downloads from WorkManager
            val activePlaylistWorks = playlistWorks.filter {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }
            val activePlaylistTags = mutableSetOf<String>()

            for (work in activePlaylistWorks) {
                val playlistTag = work.tags.firstOrNull { it.startsWith("playlist_dl_") }
                val playlistId = playlistTag?.removePrefix("playlist_dl_")
                    ?: work.tags.firstOrNull {
                        it != PlaylistDownloadWorker::class.java.name &&
                            !it.startsWith("dl_") &&
                            !it.startsWith("tag_")
                    }

                if (playlistTag != null) {
                    activePlaylistTags.add(playlistTag)
                }

                val playlist = playlistId?.let { appDatabase.playlistRepository().getPlaylistById(it) }
                val total = work.progress.getInt(PlaylistDownloadWorker.PROGRESS_TOTAL, 0)
                val current = work.progress.getInt(PlaylistDownloadWorker.PROGRESS_CURRENT, 0)
                val effectiveTotal = if (total > 0) total else (playlist?.songs?.size ?: 0)
                val progressFloat = if (effectiveTotal > 0) {
                    (current.toFloat() / effectiveTotal.toFloat()).coerceIn(0f, 1f)
                } else null
                val percent = progressFloat?.let { (it * 100).toInt() }
                val title = playlist?.info?.title?.takeIf { it.isNotBlank() } ?: "Playlist Download"
                val subtitle = if (effectiveTotal > 0) "$current of $effectiveTotal · ${percent ?: 0}%" else "Downloading…"

                activeItems.add(
                    ActiveDownloadDisplayItem(
                        id = work.id.toString(),
                        title = title,
                        subtitle = subtitle,
                        progress = progressFloat,
                        progressPercent = percent,
                        isPlaylist = true,
                        playlistId = playlistId,
                        workId = work.id.toString()
                    )
                )
            }

            // 2. Process active single song downloads from WorkManager
            val activeSongWorks = songWorks.filter {
                it.state == WorkInfo.State.RUNNING ||
                    it.state == WorkInfo.State.ENQUEUED ||
                    it.state == WorkInfo.State.BLOCKED
            }

            for (work in activeSongWorks) {
                val belongsToActivePlaylist = work.tags.any { tag -> tag in activePlaylistTags }
                if (belongsToActivePlaylist) continue

                val songId = work.tags.firstOrNull { it.startsWith("dl_playlist_song_") }?.removePrefix("dl_playlist_song_")
                    ?: work.tags.firstOrNull { it.startsWith("dl_liked_") }?.removePrefix("dl_liked_")
                    ?: work.tags.firstOrNull { it.startsWith("dl_") }?.removePrefix("dl_")
                    ?: work.tags.firstOrNull { it.startsWith(Constants.Downloads.DOWNLOADED_PLAYLIST_ID) }
                        ?.removePrefix(Constants.Downloads.DOWNLOADED_PLAYLIST_ID)

                val song = songId?.let { appDatabase.songRepository().getSong(it) }
                val title = song?.title?.takeIf { it.isNotBlank() } ?: "Downloading track…"
                val subtitle = song?.artist?.takeIf { it.isNotBlank() } ?: "Downloading…"

                activeItems.add(
                    ActiveDownloadDisplayItem(
                        id = work.id.toString(),
                        title = title,
                        subtitle = subtitle,
                        progress = null,
                        progressPercent = null,
                        isPlaylist = false,
                        workId = work.id.toString()
                    )
                )
            }

            // 3. Process CloudOfflineRepository downloads
            for (download in cloudDownloads) {
                when (download.status) {
                    OfflineDownloadStatus.QUEUED, OfflineDownloadStatus.DOWNLOADING -> {
                        activeItems.add(
                            ActiveDownloadDisplayItem(
                                id = download.downloadId,
                                title = download.title.ifBlank { "Cloud Download" },
                                subtitle = if (download.status == OfflineDownloadStatus.DOWNLOADING) {
                                    val pct = download.progress?.let { (it * 100).toInt() }
                                    if (pct != null) "Downloading… ($pct%)" else "Downloading…"
                                } else {
                                    "Queued"
                                },
                                progress = download.progress,
                                progressPercent = download.progress?.let { (it * 100).toInt() },
                                isPlaylist = false,
                                sourceUri = download.sourceUri
                            )
                        )
                    }
                    OfflineDownloadStatus.FAILED -> {
                        failed.add(download)
                    }
                    OfflineDownloadStatus.COMPLETE -> {
                        val localFile = download.localPath?.let(::File)
                        if (localFile != null && localFile.isFile && localFile.length() > 0L) {
                            val fileSize = localFile.length()
                            val song = resolveSongForDownload(download, localFile)
                            completedItems.add(
                                CloudDownloadedSongItem(
                                    song = song,
                                    download = download,
                                    fileSizeBytes = fileSize
                                )
                            )
                        } else {
                            dao.deleteBySourceUri(download.sourceUri)
                        }
                    }
                }
            }

            // 4. Process local YouTube / Playlist downloads
            for (ySong in localSongs) {
                val path = ySong.audioFilePath
                val file = path?.let(::File)
                if (file != null && file.isFile && file.length() > 0L) {
                    val fileSize = file.length()
                    val song = resolveSongFromLocal(ySong, file)
                    completedItems.add(
                        CloudDownloadedSongItem(
                            song = song,
                            download = OfflineDownload(
                                downloadId = ySong.youtubeId,
                                sourceUri = "youtube://${ySong.youtubeId}",
                                status = OfflineDownloadStatus.COMPLETE,
                                bytesDownloaded = fileSize,
                                totalBytes = fileSize,
                                localPath = file.absolutePath,
                                errorMessage = null,
                                title = song.title,
                                provider = "youtube"
                            ),
                            fileSizeBytes = fileSize
                        )
                    )
                }
            }

            // 5. Process local device storage songs if in ALL_OFFLINE mode
            if (filterMode == DownloadFilterMode.ALL_OFFLINE) {
                val localEntities = musicDao.getSongsBySourceType(0) // SourceType.LOCAL
                for (localEntity in localEntities) {
                    val path = localEntity.filePath
                    val file = if (path.isNotBlank()) File(path) else null
                    if (file != null && file.isFile && file.length() > 0L) {
                        val fileSize = file.length()
                        val nativeSong = localEntity.toSong()
                        completedItems.add(
                            CloudDownloadedSongItem(
                                song = nativeSong,
                                download = OfflineDownload(
                                    downloadId = nativeSong.id,
                                    sourceUri = nativeSong.contentUriString,
                                    status = OfflineDownloadStatus.COMPLETE,
                                    bytesDownloaded = fileSize,
                                    totalBytes = fileSize,
                                    localPath = file.absolutePath,
                                    errorMessage = null,
                                    title = nativeSong.title,
                                    provider = "local"
                                ),
                                fileSizeBytes = fileSize
                            )
                        )
                    }
                }
            }

            // Deduplicate completed items by song id or path
            val uniqueCompleted = completedItems
                .distinctBy { it.song.id }
                .sortedByDescending { it.song.dateAdded }

            val totalUsedBytes = calculateStorageUsed(context, uniqueCompleted, filterMode == DownloadFilterMode.ALL_OFFLINE)

            var activeRemainingSongs = 0
            for (activeItem in activeItems) {
                if (activeItem.isPlaylist && activeItem.playlistId != null) {
                    val pl = appDatabase.playlistRepository().getPlaylistById(activeItem.playlistId)
                    val plTotal = pl?.songs?.size ?: 0
                    val pct = activeItem.progress ?: 0f
                    val currentDownloaded = (pct * plTotal).toInt()
                    activeRemainingSongs += (plTotal - currentDownloaded).coerceAtLeast(1)
                } else {
                    activeRemainingSongs += 1
                }
            }

            val validSelectedIds = selectedIds.filter { id ->
                uniqueCompleted.any { it.song.id == id }
            }.toSet()

            CloudDownloadsUiState(
                completedDownloads = uniqueCompleted,
                activeDownloads = activeItems,
                failedDownloads = failed,
                storageUsedBytes = totalUsedBytes,
                totalCompletedCount = uniqueCompleted.size,
                totalCount = uniqueCompleted.size + activeRemainingSongs + failed.size,
                selectedSongIds = validSelectedIds,
                isSelectionMode = validSelectedIds.isNotEmpty(),
                isDownloadsPaused = isPaused,
                isLoading = false,
                filterMode = filterMode
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudDownloadsUiState()
    )

    private fun calculateStorageUsed(
        context: Context,
        completedItems: List<CloudDownloadedSongItem>,
        isAllOffline: Boolean
    ): Long {
        if (isAllOffline) {
            return completedItems.sumOf { it.fileSizeBytes }
        }
        var totalBytes = 0L
        try {
            val audioDir = PixelMusicHelper.getDownloadDirectory(
                context,
                Constants.Downloads.AUDIO_FILES_FOLDER
            )
            if (audioDir.exists()) {
                totalBytes += audioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } catch (_: Exception) {}

        try {
            val cloudDir = CloudOfflineRepository.downloadDirectory(context)
            if (cloudDir.exists()) {
                totalBytes += cloudDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } catch (_: Exception) {}

        try {
            val thumbDir = PixelMusicHelper.getDownloadDirectory(
                context,
                Constants.Downloads.THUMBNAILS_FOLDER
            )
            if (thumbDir.exists()) {
                totalBytes += thumbDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } catch (_: Exception) {}

        if (totalBytes <= 0L) {
            totalBytes = completedItems.sumOf { it.fileSizeBytes }
        }
        return totalBytes
    }

    private suspend fun resolveSongFromLocal(
        ySong: com.unshoo.pixelmusic.data.model.youtube.Song,
        file: File
    ): Song {
        val songFromPath = musicDao.getSongByPath(file.absolutePath)?.toSong()
        if (songFromPath != null) return songFromPath

        val unifiedId = YouTubeIdUtils.toUnifiedYoutubeSongId(ySong.youtubeId)
        val songFromId = musicDao.getSongByIdOnce(unifiedId)?.toSong()
        if (songFromId != null) return songFromId

        val primaryArtistName = ySong.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val primaryArtistId = YouTubeIdUtils.toUnifiedYoutubeArtistId(primaryArtistName)
        val albumName = ySong.album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
        val albumId = YouTubeIdUtils.toUnifiedYoutubeAlbumId(albumName)
        val durationMs = parseDurationMillis(ySong.duration)
        val artworkPath = ySong.thumbnailPath?.takeIf { File(it).exists() }
            ?: file.parentFile?.resolve("${ySong.youtubeId}.jpg")?.takeIf { it.exists() }?.absolutePath
            ?: ySong.thumbnailHref

        return Song(
            id = "youtube_${ySong.youtubeId}",
            title = ySong.title.ifBlank { "Downloaded Track" },
            artist = primaryArtistName,
            artistId = primaryArtistId,
            artists = listOf(
                com.unshoo.pixelmusic.data.model.ArtistRef(
                    id = primaryArtistId,
                    name = primaryArtistName,
                    isPrimary = true
                )
            ),
            album = albumName,
            albumId = albumId,
            albumArtist = null,
            path = file.absolutePath,
            contentUriString = "youtube://${ySong.youtubeId}",
            albumArtUriString = artworkPath,
            duration = durationMs,
            genre = ySong.genre ?: "YouTube Music",
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            discNumber = null,
            year = 0,
            dateAdded = ySong.downloadTimestamp.takeIf { it > 0 } ?: file.lastModified(),
            dateModified = file.lastModified(),
            mimeType = "audio/webm",
            bitrate = null,
            sampleRate = null,
            youtubeId = ySong.youtubeId,
            albumBrowseId = ySong.albumBrowseId
        )
    }

    private fun parseDurationMillis(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0] * 1000L
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 0L
        }
    }

    private suspend fun resolveSongForDownload(download: OfflineDownload, file: File): Song {
        val songFromPath = musicDao.getSongByPath(file.absolutePath)?.toSong()
        if (songFromPath != null) return songFromPath

        val songFromUri = musicDao.getSongIdByContentUri(download.sourceUri)
            ?.let { musicDao.getSongByIdOnce(it)?.toSong() }
        if (songFromUri != null) return songFromUri

        val ytId = download.sourceUri.removePrefix("youtube://").substringBefore('?')
        val songId = if (ytId.isNotBlank()) {
            YouTubeIdUtils.toUnifiedSongId(ytId).toString()
        } else {
            download.downloadId
        }

        val artworkPath = file.parentFile?.resolve("${file.nameWithoutExtension}.jpg")
            ?.takeIf { it.isFile && it.length() > 0L }?.absolutePath

        return Song(
            id = songId,
            title = download.title.ifBlank { "Offline Track" },
            artist = "Offline",
            artistId = 0L,
            album = "Downloads",
            albumId = 0L,
            path = file.absolutePath,
            contentUriString = download.sourceUri,
            albumArtUriString = artworkPath,
            duration = 0L,
            mimeType = "audio/webm",
            bitrate = null,
            sampleRate = null,
            youtubeId = ytId.takeIf { it.isNotBlank() }
        )
    }

    fun toggleSelection(songId: String) {
        val current = selectedSongIdsFlow.value
        selectedSongIdsFlow.value = if (current.contains(songId)) {
            current - songId
        } else {
            current + songId
        }
    }

    fun selectAll() {
        val allIds = uiState.value.completedDownloads.map { it.song.id }.toSet()
        selectedSongIdsFlow.value = allIds
    }

    fun clearSelection() {
        selectedSongIdsFlow.value = emptySet()
    }

    fun cancelAllActiveDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            pausedActiveDownloads = emptyList()
            isDownloadsPausedFlow.value = false
            workManager.cancelAllWorkByTag(PlaylistDownloadWorker::class.java.name)
            workManager.cancelAllWorkByTag(SongDownloadWorker::class.java.name)
            uiState.value.activeDownloads.forEach { item ->
                cancelActiveDownload(item)
            }
            PixelMusicNotificationManager.cancelAllDownloadNotifications(context)
        }
    }

    fun togglePauseDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isDownloadsPausedFlow.value) {
                // Resume downloads
                isDownloadsPausedFlow.value = false
                val toResume = pausedActiveDownloads.ifEmpty { uiState.value.activeDownloads }
                pausedActiveDownloads = emptyList()
                val downloadRepo = DownloadRepository(context)
                for (item in toResume) {
                    if (item.isPlaylist && !item.playlistId.isNullOrBlank()) {
                        val pl = appDatabase.playlistRepository().getPlaylistById(item.playlistId)
                        if (pl != null) {
                            downloadRepo.downloadPlaylist(pl)
                        }
                    } else if (!item.sourceUri.isNullOrBlank()) {
                        repository.retry(item.sourceUri)
                    }
                }
            } else {
                // Pause downloads
                val currentActive = uiState.value.activeDownloads
                pausedActiveDownloads = currentActive
                isDownloadsPausedFlow.value = true
                workManager.cancelAllWorkByTag(PlaylistDownloadWorker::class.java.name)
                workManager.cancelAllWorkByTag(SongDownloadWorker::class.java.name)
                currentActive.forEach { item ->
                    if (item.isPlaylist && !item.playlistId.isNullOrBlank()) {
                        workManager.cancelAllWorkByTag("playlist_dl_${item.playlistId}")
                        PixelMusicNotificationManager.cancelPlaylistDownloadNotification(context, item.playlistId)
                    }
                }
            }
        }
    }

    fun cancelActiveDownload(item: ActiveDownloadDisplayItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (item.isPlaylist && !item.playlistId.isNullOrBlank()) {
                workManager.cancelAllWorkByTag("playlist_dl_${item.playlistId}")
                PixelMusicNotificationManager.cancelPlaylistDownloadNotification(context, item.playlistId)
            } else if (!item.sourceUri.isNullOrBlank()) {
                repository.remove(item.sourceUri)
            } else if (!item.workId.isNullOrBlank()) {
                val uuid = runCatching { UUID.fromString(item.workId) }.getOrNull()
                if (uuid != null) {
                    workManager.cancelWorkById(uuid)
                }
            }
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
            if (ytId != null) {
                val downloadRepo = DownloadRepository(context)
                downloadRepo.deleteSong(ytId)
                song.id.toLongOrNull()?.let { numericId ->
                    musicDao.updateSongFilePathAndParent(numericId, "", "")
                }
            }
            if (!song.contentUriString.isNullOrBlank()) {
                repository.remove(song.contentUriString)
                dao.deleteBySourceUri(song.contentUriString)
            }
            if (song.path.isNotBlank()) {
                runCatching { File(song.path).delete() }
            }
        }
    }

    fun deleteSelected() {
        val toDelete = selectedSongIdsFlow.value
        if (toDelete.isEmpty()) return

        val itemsToDelete = uiState.value.completedDownloads.filter { it.song.id in toDelete }
        viewModelScope.launch(Dispatchers.IO) {
            val downloadRepo = DownloadRepository(context)
            itemsToDelete.forEach { item ->
                val song = item.song
                val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
                if (ytId != null) {
                    downloadRepo.deleteSong(ytId)
                    song.id.toLongOrNull()?.let { numericId ->
                        musicDao.updateSongFilePathAndParent(numericId, "", "")
                    }
                }
                if (!song.contentUriString.isNullOrBlank()) {
                    repository.remove(song.contentUriString)
                    dao.deleteBySourceUri(song.contentUriString)
                }
                if (song.path.isNotBlank()) {
                    runCatching { File(song.path).delete() }
                }
            }
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }

    fun removeDownload(sourceUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ytId = if (sourceUri.startsWith("youtube://")) sourceUri.removePrefix("youtube://") else null
            if (ytId != null) {
                val downloadRepo = DownloadRepository(context)
                downloadRepo.deleteSong(ytId)
            }
            repository.remove(sourceUri)
            dao.deleteBySourceUri(sourceUri)
        }
    }

    fun retryDownload(sourceUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.retry(sourceUri)
        }
    }

    fun clearAllCompleted() {
        val allSongs = uiState.value.completedDownloads.map { it.song }
        viewModelScope.launch(Dispatchers.IO) {
            val downloadRepo = DownloadRepository(context)
            allSongs.forEach { song ->
                val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
                if (ytId != null) {
                    downloadRepo.deleteSong(ytId)
                    song.id.toLongOrNull()?.let { numericId ->
                        musicDao.updateSongFilePathAndParent(numericId, "", "")
                    }
                }
                if (!song.contentUriString.isNullOrBlank()) {
                    repository.remove(song.contentUriString)
                    dao.deleteBySourceUri(song.contentUriString)
                }
                if (song.path.isNotBlank()) {
                    runCatching { File(song.path).delete() }
                }
            }
            repository.deleteAllDownloaded()
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }
}
