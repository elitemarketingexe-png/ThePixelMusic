package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.model.LibraryTabId
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.StorageFilter
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.paging.compose.LazyPagingItems
import androidx.paging.LoadState
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.ExpressiveScrollBar
import com.unshoo.pixelmusic.presentation.components.songFastScrollLabel
import androidx.compose.ui.text.style.TextOverflow


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibrarySongsTab(
    songs: LazyPagingItems<Song>, // Changed from ImmutableList<Song>
    isLoading: Boolean, // Kept for initial load or other states, though Paging has its own
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    sortOption: SortOption, // Added sortOption parameter
    // Multi-selection parameters
    isSelectionMode: Boolean = false,
    selectedSongIds: Set<String> = emptySet(),
    onSongLongPress: (Song) -> Unit = {},
    onSongSelectionToggle: (Song) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null },
    onLocateCurrentSongVisibilityChanged: (Boolean) -> Unit = {},
    onRegisterLocateCurrentSongAction: ((() -> Unit)?) -> Unit = {},
    storageFilter: StorageFilter = StorageFilter.ALL,
    hasCurrentSong: Boolean = false
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()
    val visibilityCallback by rememberUpdatedState(onLocateCurrentSongVisibilityChanged)
    val registerActionCallback by rememberUpdatedState(onRegisterLocateCurrentSongAction)
    val songFastScrollLabelProvider = remember(songs, sortOption) {
        { index: Int ->
            songFastScrollLabel(
                song = if (index in 0 until songs.itemCount) songs.peek(index) else null,
                sortOption = sortOption
            )
        }
    }
    var lastHandledSongSortKey by remember { mutableStateOf(sortOption.storageKey) }
    var pendingSongSortScrollReset by remember { mutableStateOf(false) }
    var songSortSawRefreshLoading by remember { mutableStateOf(false) }
    val currentSongId by remember(playerViewModel) {
        playerViewModel.stablePlayerState
            .map { it.currentSong?.id }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    // Check if list is effectively empty (based on Paging state)
    // val isListEmpty = songs.itemCount == 0 && songs.loadState.refresh is LoadState.NotLoading
    
    // Scroll Handler from ViewModel (Centers playing song vertically in list)
    LaunchedEffect(Unit) {
        playerViewModel.scrollToIndexEvent.collect { index ->
            if (index >= 0) {
                 val viewportHeight = listState.layoutInfo.viewportSize.height
                 val centerOffset = -(viewportHeight / 3)
                 val firstVisible = listState.firstVisibleItemIndex
                 if (Math.abs(index - firstVisible) > 20) {
                     listState.scrollToItem(index, scrollOffset = centerOffset)
                 } else {
                     listState.animateScrollToItem(index, scrollOffset = centerOffset)
                 }
            }
        }
    }

    // Do not auto-scan the whole PagingData snapshot when the track changes. With placeholders
    // enabled that is an O(library size) main-thread loop (20k `peek` calls for a large
    // library), even though most rows are not loaded. The explicit locate action above resolves
    // the sorted position in the repository and emits `scrollToIndexEvent` instead.

    // New action just triggers the ViewModel request
    val locateCurrentSongAction: (() -> Unit)? = remember(currentSongId) {
        if (currentSongId == null) {
            null
        } else {
            {
                playerViewModel.requestLocateCurrentSong()
            }
        }
    }

    LaunchedEffect(locateCurrentSongAction) {
        registerActionCallback(locateCurrentSongAction)
    }

    LaunchedEffect(sortOption) {
        val currentSortKey = sortOption.storageKey
        if (currentSortKey == lastHandledSongSortKey) return@LaunchedEffect
        lastHandledSongSortKey = currentSortKey
        pendingSongSortScrollReset = true
        songSortSawRefreshLoading = false
        listState.scrollToItem(0)
    }

    // Apply a second reset after paging finishes refresh, to avoid key-anchor jumps.
    LaunchedEffect(songs.loadState.refresh, pendingSongSortScrollReset) {
        if (!pendingSongSortScrollReset) return@LaunchedEffect
        if (songs.loadState.refresh is LoadState.Loading) {
            songSortSawRefreshLoading = true
            return@LaunchedEffect
        }
        if (!songSortSawRefreshLoading) return@LaunchedEffect
        listState.scrollToItem(0)
        pendingSongSortScrollReset = false
    }

    val currentSongListIndex = remember(songs.itemSnapshotList, currentSongId) {
        if (currentSongId == null) -1
        else {
            val snapshot = songs.itemSnapshotList
            val indexInSnapshot = snapshot.items.indexOfFirst { it.id == currentSongId }
            if (indexInSnapshot != -1) indexInSnapshot + snapshot.placeholdersBefore else -1
        }
    }

    LaunchedEffect(currentSongListIndex, songs.itemCount, listState, currentSongId) {
        if (currentSongId == null || songs.itemCount == 0) {
            visibilityCallback(false)
            return@LaunchedEffect
        }

        if (currentSongListIndex == -1) {
            visibilityCallback(true)
            return@LaunchedEffect
        }

        snapshotFlow {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                false
            } else {
                currentSongListIndex in visibleItems.first().index..visibleItems.last().index
            }
        }
            .distinctUntilChanged()
            .collect { isVisible ->
                visibilityCallback(!isVisible)
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            visibilityCallback(false)
            registerActionCallback(null)
        }
    }

    val refreshState = songs.loadState.refresh
    val reachedEndOfPagination = songs.loadState.append.endOfPaginationReached
    val shouldShowInitialLoading = songs.itemCount == 0 && (
        isLoading || refreshState is LoadState.Loading
    )

    when {
        refreshState is LoadState.Error && songs.itemCount == 0 -> {
            val error = refreshState.error
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.library_error_loading_songs), style = MaterialTheme.typography.titleMedium)
                    Text(
                        error?.localizedMessage ?: stringResource(R.string.error_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { songs.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        shouldShowInitialLoading -> {
            // Initial loading - show skeleton placeholders
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 24.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    )
                    .fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(12, key = { "skeleton_song_$it" }) {
                    EnhancedSongListItem(
                        song = Song.emptySong(),
                        isPlaying = false,
                        isLoading = true,
                        isCurrentSong = false,
                        onMoreOptionsClick = {},
                        onClick = {}
                    )
                }
            }
        }
        songs.itemCount == 0 && refreshState is LoadState.NotLoading -> {
            LibraryExpressiveEmptyState(
                tabId = LibraryTabId.SONGS,
                storageFilter = storageFilter,
                bottomBarHeight = bottomBarHeight
            )
        }
        else -> {
            // Songs loaded
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                // Use fixed 22dp end padding to leave room for the scrollbar —
                                // songs always fill more than a screen when in the content state.
                                .padding(start = 12.dp, end = 22.dp, bottom = 6.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 26.dp,
                                        topEnd = 26.dp,
                                        bottomStart = PlayerSheetCollapsedCornerRadius,
                                        bottomEnd = PlayerSheetCollapsedCornerRadius
                                    )
                                ),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                        ) {
                            items(
                                count = songs.itemCount,
                                key = { index ->
                                    if (index in 0 until songs.itemCount) {
                                        songs.peek(index)?.id ?: "song_placeholder_$index"
                                    } else {
                                        "song_placeholder_$index"
                                    }
                                },
                                contentType = { index ->
                                    if (songs.peek(index) != null) "song" else "placeholder"
                                }
                            ) { index ->
                                val song = songs[index]
                                
                                if (song != null) {
                                    val isSelected = selectedSongIds.contains(song.id)
                                    
                                    val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                        { songFromListItem -> onMoreOptionsClick(songFromListItem) }
                                    }
                                    
                                    // In selection mode, click toggles selection instead of playing
                                    val rememberedOnClick: () -> Unit = remember(song.id, isSelectionMode) {
                                        if (isSelectionMode) {
                                            { onSongSelectionToggle(song) }
                                        } else {
                                            { playerViewModel.showAndPlaySongFromLibrary(song) }
                                        }
                                    }
                                    
                                    val rememberedOnLongPress: () -> Unit = remember(song.id) {
                                        { onSongLongPress(song) }
                                    }

                                    LibraryPlaybackAwareSongItem(
                                        song = song,
                                        playerViewModel = playerViewModel,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        selectionIndex = if (isSelectionMode) getSelectionIndex(song.id) else null,
                                        onLongPress = rememberedOnLongPress,
                                        onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                        onClick = rememberedOnClick
                                    )
                                } else {
                                     // Placeholder
                                     EnhancedSongListItem(
                                        song = Song.emptySong(),
                                        isPlaying = false,
                                        isLoading = true,
                                        isCurrentSong = false,
                                        onMoreOptionsClick = {},
                                        onClick = {}
                                     )
                                }
                            }
                        }
                        
                        // ScrollBar Overlay
                        val bottomPadding = if (hasCurrentSong)
                            bottomBarHeight + MiniPlayerHeight + 16.dp 
                        else 
                            bottomBarHeight + 16.dp

                        ExpressiveScrollBar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 16.dp, bottom = bottomPadding),
                            listState = listState,
                            dragLabelProvider = songFastScrollLabelProvider
                        )
                    }
                }
            }
        }
    }
}
