package com.unshoo.pixelmusic.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
internal data class LibrarySongPlaybackUiState(
    val isCurrentSong: Boolean = false,
    val isPlaying: Boolean = false
)

/**
 * OPTIMIZED (adopted from PixelMusic): each row self-observes [PlayerViewModel.stablePlayerState]
 * through a per-row derived flow (`remember(song.id, playerViewModel)`) instead of receiving
 * `currentSongId`/`isPlaying` from the parent list.
 *
 * Result: when playback state changes, ONLY the row whose song changed recomposes —
 * the whole list no longer redraws, keeping scrolling smooth on mid-range devices.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    modifier: Modifier = Modifier,
    song: Song,
    playerViewModel: PlayerViewModel,
    albumArtSize: Dp = 50.dp,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val playbackUiState by remember(song.id, playerViewModel) {
        playerViewModel.stablePlayerState
            .map { state ->
                val isCurrentSong = state.currentSong?.id == song.id
                LibrarySongPlaybackUiState(
                    isCurrentSong = isCurrentSong,
                    isPlaying = isCurrentSong && state.isPlaying
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = LibrarySongPlaybackUiState())

    EnhancedSongListItem(
        modifier = modifier,
        song = song,
        isPlaying = playbackUiState.isPlaying,
        isCurrentSong = playbackUiState.isCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        onClick = onClick
    )
}
