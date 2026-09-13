package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.OfflineTrackDao
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.offline.CloudOfflineRepository
import com.unshoo.pixelmusic.data.offline.OfflineDownload
import com.unshoo.pixelmusic.data.offline.OfflineDownloadStatus
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CloudDownloadedSongItem(
    val song: Song,
    val download: OfflineDownload,
    val fileSizeBytes: Long
)

data class CloudDownloadsUiState(
    val completedDownloads: List<CloudDownloadedSongItem> = emptyList(),
    val activeDownloads: List<OfflineDownload> = emptyList(),
    val failedDownloads: List<OfflineDownload> = emptyList(),
    val storageUsedBytes: Long = 0L,
    val totalCompletedCount: Int = 0,
    val totalCount: Int = 0,
    val selectedSongIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = true
)

@HiltViewModel
class CloudDownloadsViewModel @Inject constructor(
    private val repository: CloudOfflineRepository,
    private val dao: OfflineTrackDao,
    private val musicDao: MusicDao
) : ViewModel() {

    private val selectedSongIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<CloudDownloadsUiState> = combine(
        repository.observeAll(),
        selectedSongIdsFlow
    ) { allDownloads, selectedIds ->
        withContext(Dispatchers.IO) {
            val active = ArrayList<OfflineDownload>()
            val failed = ArrayList<OfflineDownload>()
            val completedItems = ArrayList<CloudDownloadedSongItem>()
            var totalUsedBytes = 0L

            for (download in allDownloads) {
                when (download.status) {
                    OfflineDownloadStatus.QUEUED, OfflineDownloadStatus.DOWNLOADING -> {
                        active.add(download)
                    }
                    OfflineDownloadStatus.FAILED -> {
                        failed.add(download)
                    }
                    OfflineDownloadStatus.COMPLETE -> {
                        val localFile = download.localPath?.let(::File)
                        if (localFile != null && localFile.isFile && localFile.length() > 0L) {
                            val fileSize = localFile.length()
                            totalUsedBytes += fileSize

                            val song = resolveSongForDownload(download, localFile)
                            completedItems.add(
                                CloudDownloadedSongItem(
                                    song = song,
                                    download = download,
                                    fileSizeBytes = fileSize
                                )
                            )
                        } else {
                            // Disk file missing or empty; remove stale DB entry
                            dao.deleteBySourceUri(download.sourceUri)
                        }
                    }
                }
            }

            val validSelectedIds = selectedIds.filter { id ->
                completedItems.any { it.song.id == id }
            }.toSet()

            CloudDownloadsUiState(
                completedDownloads = completedItems,
                activeDownloads = active,
                failedDownloads = failed,
                storageUsedBytes = totalUsedBytes,
                totalCompletedCount = completedItems.size,
                totalCount = allDownloads.size,
                selectedSongIds = validSelectedIds,
                isSelectionMode = validSelectedIds.isNotEmpty(),
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudDownloadsUiState()
    )

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

    fun deleteSelected() {
        val toDelete = selectedSongIdsFlow.value
        if (toDelete.isEmpty()) return

        val itemsToDelete = uiState.value.completedDownloads.filter { it.song.id in toDelete }
        viewModelScope.launch(Dispatchers.IO) {
            itemsToDelete.forEach { item ->
                repository.remove(item.download.sourceUri)
            }
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }

    fun removeDownload(sourceUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.remove(sourceUri)
        }
    }

    fun retryDownload(sourceUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.retry(sourceUri)
        }
    }

    fun clearAllCompleted() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllDownloaded()
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }
}
