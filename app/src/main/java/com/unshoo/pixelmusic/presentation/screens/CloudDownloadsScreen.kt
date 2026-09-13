package com.unshoo.pixelmusic.presentation.screens

import android.app.Activity
import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.offline.OfflineDownload
import com.unshoo.pixelmusic.data.offline.OfflineDownloadStatus
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SongInfoBottomSheet
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.viewmodel.CloudDownloadsUiState
import com.unshoo.pixelmusic.presentation.viewmodel.CloudDownloadsViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel

@OptIn(
    UnstableApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@Composable
fun CloudDownloadsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: CloudDownloadsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    val completedSongs = remember(uiState.completedDownloads) {
        uiState.completedDownloads.map { it.song }
    }

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 80.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Storage summary card
                item(key = "storage_summary_card") {
                    StorageSummaryCard(
                        uiState = uiState,
                        onPlayAll = {
                            if (completedSongs.isNotEmpty()) {
                                playerViewModel.playSongs(completedSongs, completedSongs.first(), "Downloads")
                            }
                        },
                        onShuffleAll = {
                            if (completedSongs.isNotEmpty()) {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = completedSongs,
                                    queueName = "Downloads",
                                    startAtZero = true
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // Active downloads section
                if (uiState.activeDownloads.isNotEmpty()) {
                    item(key = "active_downloads_header") {
                        SectionHeader(
                            title = stringResource(
                                R.string.cloud_downloads_section_count,
                                stringResource(R.string.cloud_downloads_active_section),
                                uiState.activeDownloads.size
                            ),
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.activeDownloads,
                        key = { "active_${it.downloadId}" }
                    ) { download ->
                        ActiveDownloadCard(
                            download = download,
                            onCancel = { viewModel.removeDownload(download.sourceUri) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                }

                // Failed downloads section
                if (uiState.failedDownloads.isNotEmpty()) {
                    item(key = "failed_downloads_header") {
                        SectionHeader(
                            title = stringResource(
                                R.string.cloud_downloads_section_count,
                                stringResource(R.string.cloud_downloads_failed_section),
                                uiState.failedDownloads.size
                            ),
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.failedDownloads,
                        key = { "failed_${it.downloadId}" }
                    ) { download ->
                        FailedDownloadCard(
                            download = download,
                            onRetry = { viewModel.retryDownload(download.sourceUri) },
                            onDismiss = { viewModel.removeDownload(download.sourceUri) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                        )
                    }
                }

                // Completed downloads section
                if (uiState.completedDownloads.isNotEmpty()) {
                    item(key = "completed_downloads_header") {
                        SectionHeader(
                            title = stringResource(
                                R.string.cloud_downloads_section_count,
                                stringResource(R.string.cloud_downloads_completed_section),
                                uiState.completedDownloads.size
                            ),
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = uiState.completedDownloads,
                        key = { "completed_${it.download.downloadId}" }
                    ) { item ->
                        val isSelected = item.song.id in uiState.selectedSongIds
                        val isCurrent = stablePlayerState.currentSong?.id == item.song.id
                        val isPlaying = isCurrent && stablePlayerState.isPlaying

                        EnhancedSongListItem(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            song = item.song,
                            isCurrentSong = isCurrent,
                            isPlaying = isPlaying,
                            isSelected = isSelected,
                            isSelectionMode = uiState.isSelectionMode,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(item.song.id)
                                } else {
                                    playerViewModel.playSongs(
                                        songsToPlay = completedSongs,
                                        startSong = item.song,
                                        queueName = "Downloads"
                                    )
                                }
                            },
                            onLongPress = {
                                viewModel.toggleSelection(item.song.id)
                            },
                            onMoreOptionsClick = { song ->
                                playerViewModel.selectSongForInfo(song)
                                showSongInfoBottomSheet = true
                            }
                        )
                    }
                }

                // Empty state
                if (uiState.totalCount == 0) {
                    item(key = "empty_downloads_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.cloud_downloads_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.cloud_downloads_empty_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top App Bar
        TopDownloadsBar(
            isSelectionMode = uiState.isSelectionMode,
            selectedCount = uiState.selectedSongIds.size,
            allSelected = uiState.selectedSongIds.size == uiState.completedDownloads.size && uiState.completedDownloads.isNotEmpty(),
            hasCompleted = uiState.completedDownloads.isNotEmpty(),
            onBack = { navController.popBackStack() },
            onToggleSelectAll = {
                if (uiState.selectedSongIds.size == uiState.completedDownloads.size) {
                    viewModel.clearSelection()
                } else {
                    viewModel.selectAll()
                }
            },
            onDeleteSelected = {
                viewModel.deleteSelected()
            },
            onPlaySelected = {
                val selectedItems = uiState.completedDownloads.filter { it.song.id in uiState.selectedSongIds }
                if (selectedItems.isNotEmpty()) {
                    val songs = selectedItems.map { it.song }
                    playerViewModel.playSongs(songs, songs.first(), "Downloads")
                }
            },
            onCloseSelection = {
                viewModel.clearSelection()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Song info bottom sheet
        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val currentSong = selectedSongForInfo!!
            SongInfoBottomSheet(
                song = currentSong,
                playerViewModel = playerViewModel,
                isFavorite = favoriteSongIds.contains(currentSong.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showSongInfoBottomSheet = false
                },
                onDeleteFromDevice = { activity, song, onResult ->
                    viewModel.removeDownload(song.contentUriString)
                    onResult(true)
                },
                onNavigateToAlbum = {},
                onNavigateToArtist = {},
                onEditSong = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                generateAiMetadata = { Result.failure(UnsupportedOperationException()) },
                removeFromListTrigger = {
                    viewModel.removeDownload(currentSong.contentUriString)
                }
            )
        }
    }
}

@Composable
private fun TopDownloadsBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    hasCompleted: Boolean,
    onBack: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onPlaySelected: () -> Unit,
    onCloseSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSelectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onCloseSelection,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlaySelected) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.cloud_download_play_selected),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onToggleSelectAll) {
                        Icon(
                            imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                            contentDescription = stringResource(
                                if (allSelected) R.string.cloud_download_deselect_all else R.string.cloud_download_select_all
                            )
                        )
                    }
                    IconButton(onClick = onDeleteSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.cloud_download_delete_selected),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.cloud_downloads_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (hasCompleted) {
                    TextButton(onClick = onToggleSelectAll) {
                        Text(
                            text = stringResource(R.string.cloud_download_select_all),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(
    uiState: CloudDownloadsUiState,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val formattedStorage = remember(uiState.storageUsedBytes) {
        Formatter.formatFileSize(context, uiState.storageUsedBytes)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.cloud_downloads_storage_used),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formattedStorage,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            R.string.cloud_downloads_storage_summary,
                            uiState.totalCompletedCount,
                            uiState.totalCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (uiState.completedDownloads.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.cloud_download_play_all))
                    }

                    FilledTonalButton(
                        onClick = onShuffleAll,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.cloud_download_shuffle_all))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun ActiveDownloadCard(
    download: OfflineDownload,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = download.title.ifBlank { stringResource(R.string.cloud_downloads_unknown_track) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val statusText = if (download.status == OfflineDownloadStatus.DOWNLOADING) {
                    val progressInt = download.progress?.let { (it * 100).toInt() }
                    if (progressInt != null) {
                        stringResource(R.string.cloud_downloading) + " (" + stringResource(R.string.cloud_download_progress, progressInt) + ")"
                    } else {
                        stringResource(R.string.cloud_downloading)
                    }
                } else {
                    stringResource(R.string.cloud_download_queued)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                if (download.progress != null) {
                    LinearProgressIndicator(
                        progress = { download.progress!! },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FailedDownloadCard(
    download: OfflineDownload,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = download.title.ifBlank { stringResource(R.string.cloud_downloads_unknown_track) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = download.errorMessage ?: stringResource(R.string.cloud_downloads_failed_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cloud_download_retry),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cloud_remove_download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
