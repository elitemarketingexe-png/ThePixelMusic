@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.unshoo.pixelmusic.presentation.screens

import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely

import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import java.util.Calendar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.presentation.components.QuickPicksSection
import com.unshoo.pixelmusic.presentation.viewmodel.QuickPicksViewModel
import com.unshoo.pixelmusic.data.ads.AdManager
import com.unshoo.pixelmusic.presentation.components.AdSupportCard
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreUiState
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.AutoAwesome
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.compositeOver
private val ExpressiveSmallShape = AbsoluteSmoothCornerShape(16.dp, 70)
private val ExpressiveMediumShape = AbsoluteSmoothCornerShape(20.dp, 70)
private val ExpressiveLargeShape = AbsoluteSmoothCornerShape(26.dp, 70)

private fun isBentoSection(title: String, itemSize: Int): Boolean {
    val t = title.lowercase()
    return itemSize >= 4 && (
        t.contains("featured") ||
        t.contains("supermix") ||
        t.contains("curated") ||
        t.contains("mixed for you") ||
        t.contains("new releases") ||
        t.contains("forgotten favorites") ||
        t.contains("forgotten")
    )
}

@UnstableApi
@Composable
fun ExploreScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    quickPicksViewModel: QuickPicksViewModel = hiltViewModel()
) {
    val sheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = sheetState == com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState.COLLAPSED) {
        navController.navigateToTopLevelSafely(com.unshoo.pixelmusic.presentation.navigation.Screen.Home.route)
    }

    val uiState by exploreViewModel.uiState.collectAsStateWithLifecycle()

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var isManualRefreshing by remember { mutableStateOf(false) }
    val isRefreshing = uiState.isRefreshing || isManualRefreshing

    LaunchedEffect(uiState.selectedFilter) {
        if (uiState.selectedFilter == "Charts" || uiState.selectedFilter == "All") {
            exploreViewModel.loadChartsIfNeeded()
        }
    }

    val quickPicks by quickPicksViewModel.quickPicks.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying by remember { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSongId by remember { derivedStateOf { stablePlayerState.currentSong?.id } }
    val quickPicksDisplayMode by playerViewModel.quickPicksDisplayMode.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val isScrolled = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.homePageContinuation, uiState.isContinuationLoading) {
        if (shouldLoadMore && uiState.homePageContinuation != null && !uiState.isContinuationLoading && !uiState.isLoading && !isRefreshing) {
            exploreViewModel.loadMore()
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    val backgroundBrush = remember(surfaceColor, primaryColor, tertiaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.18f),
                tertiaryColor.copy(alpha = 0.08f),
                surfaceColor.copy(alpha = 0.85f),
                surfaceColor
            ),
            endY = 1400f
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExploreTopBar(
                onSettingsClick = {
                    navController.navigateSafely(Screen.Settings.route)
                },
                onCreateClick = {
                    navController.navigateSafely(Screen.SmartMix.route)
                },
                isScrolled = isScrolled.value
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isManualRefreshing = true
                    exploreViewModel.loadData(forceRefresh = true)
                    exploreViewModel.loadChartsIfNeeded(forceRefresh = true)
                    quickPicksViewModel.refresh(force = true)
                    kotlinx.coroutines.delay(1000)
                    isManualRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {
                if (uiState.isLoading && uiState.homePageSections.isEmpty() && uiState.newReleaseAlbums.isEmpty() && uiState.chartsPage == null) {
                    com.unshoo.pixelmusic.presentation.components.ExploreSkeletonGrid(
                        paddingValues = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + (if (currentSongId != null) com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight else 0.dp)
                        )
                    )
                } else if (uiState.error != null && uiState.homePageSections.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { exploreViewModel.loadData() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    val homeSectionsRaw = if (uiState.selectedFilter == "All") {
                        if (uiState.activeMoodChip != null) {
                            uiState.explorePageSections
                        } else {
                            uiState.homePageSections.ifEmpty { uiState.explorePageSections }
                        }
                    } else {
                        uiState.homePageSections
                    }
                    data class ProcessedSection(
                        val section: HomePage.Section,
                        val isSimilar: Boolean,
                        val isBento: Boolean
                    )

                    val (fromYourLibraryAlbums, remainingSections) = remember(homeSectionsRaw) {
                        var libraryAlbums = emptyList<AlbumItem>()
                        val remaining = ArrayList<ProcessedSection>(homeSectionsRaw.size)

                        for (section in homeSectionsRaw) {
                            val title = section.title.lowercase()
                            if (title.contains("from your library")) {
                                libraryAlbums = section.items.filterIsInstance<AlbumItem>()
                                continue
                            }
                            val isLocalDuplicate = title.contains("local")
                            if (!isLocalDuplicate && section.items.isNotEmpty()) {
                                if (title.contains("trending") ||
                                    title.contains("long listens") ||
                                    title.contains("new music videos") ||
                                    title.contains("quick picks") ||
                                    title.contains("quickpicks")
                                ) {
                                    continue
                                }
                                val isSimilar = section.title.startsWith("Similar to", ignoreCase = true) ||
                                        section.title.contains("Fans also like", ignoreCase = true) ||
                                        section.title.contains("Similar", ignoreCase = true)
                                val isBento = !isSimilar && isBentoSection(section.title, section.items.size)
                                remaining.add(ProcessedSection(section, isSimilar, isBento))
                            }
                        }
                        Pair(libraryAlbums, remaining)
                    }
                    val bottomPadding = if (currentSongId != null) MiniPlayerHeight else 0.dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 2. Category Filter Chips (All, Smart Mix, For You, Charts, Recap)
                        item(key = "explore_category_filters", contentType = "CategoryFilters") {
                            var showMoodRow by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val categories = listOf("All", "Smart Mix", "For You", "New Releases", "Charts", "Recap")
                                        categories.forEach { category ->
                                            FilterChip(
                                                selected = uiState.selectedFilter == category,
                                                onClick = { exploreViewModel.setSelectedFilter(category) },
                                                label = { Text(category) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                border = null
                                            )
                                        }
                                    }
                                    
                                    // Mood chip dropdown arrow
                                    if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") && uiState.moodChips.isNotEmpty()) {
                                        androidx.compose.material3.IconButton(
                                            onClick = { showMoodRow = !showMoodRow },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showMoodRow) androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp else androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Toggle genres",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Mood / Genre Chips (When All or For You is active and arrow clicked)
                                if (showMoodRow && (uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") && uiState.moodChips.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.moodChips.forEach { chip ->
                                            val isSelected = uiState.activeMoodChip == chip
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val newChip = if (isSelected) null else chip
                                                    exploreViewModel.selectMoodChip(newChip)
                                                },
                                                label = { Text(chip.title) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                border = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter == "Recap") {
                            item(key = "explore_recap_view") {
                                RecapScreen(
                                    navController = navController,
                                    playerViewModel = playerViewModel
                                )
                            }
                        }

                        // 3. AI Smart Mix Studio Card (Hero CTA)
                        val showSmartMixCard = when (uiState.selectedFilter) {
                            "Smart Mix" -> true
                            "All" -> AdManager.hasRecentlySupported(context)
                            else -> false
                        }
                        if (showSmartMixCard) {
                            item(key = "smart_mix_studio_card") {
                                SmartMixStudioHeroCard(
                                    onClick = { navController.navigateSafely(Screen.SmartMix.route) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                )
                            }
                        }

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "Charts") &&
                            uiState.chartsPage != null && uiState.chartsPage!!.sections.isNotEmpty()) {
                            uiState.chartsPage!!.sections.forEachIndexed { index, chartSection ->
                                item(key = "chart_${chartSection.title}_${index}_header") {
                                    SectionHeader(title = chartSection.title)
                                }

                                val songItems = chartSection.items.filterIsInstance<SongItem>()
                                if (songItems.isNotEmpty()) {
                                    val songListNative = songItems.map { it.toNativeSong() }
                                    items(
                                        items = songListNative,
                                        key = { song -> "chart_${chartSection.title}_${index}_song_${song.id}" }
                                    ) { songNative ->
                                        EnhancedSongListItem(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            song = songNative,
                                            isPlaying = isPlaying && currentSongId == songNative.id,
                                            isCurrentSong = currentSongId == songNative.id,
                                            onClick = {
                                                playerViewModel.showAndPlaySong(
                                                    songNative,
                                                    songListNative,
                                                    chartSection.title
                                                )
                                            },
                                            onMoreOptionsClick = {
                                                playerViewModel.selectSongForInfo(songNative)
                                            }
                                        )
                                    }
                                } else {
                                    item(key = "chart_${chartSection.title}_${index}_list") {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(items = chartSection.items, key = { item ->
                                                when (item) {
                                                    is SongItem -> "chart_song_${item.id}"
                                                    is AlbumItem -> "chart_album_${item.browseId}"
                                                    is ArtistItem -> "chart_artist_${item.id}"
                                                    is PlaylistItem -> "chart_playlist_${item.id}"
                                                    else -> "chart_item_${item.hashCode()}"
                                                }
                                            }) { item ->
                                                when (item) {
                                                    is AlbumItem -> {
                                                        AlbumCarouselItem(
                                                            album = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                                            }
                                                        )
                                                    }
                                                    is ArtistItem -> {
                                                        ArtistCardItem(
                                                            artist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                                            },
                                                            playerViewModel = playerViewModel
                                                        )
                                                    }
                                                    is PlaylistItem -> {
                                                        PlaylistCardItem(
                                                            playlist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                                                            }
                                                        )
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter == "All") {
                            item(key = "explore_donation_support_card") {
                                AdSupportCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }





                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") &&
                            quickPicks.isNotEmpty()
                        ) {
                            item(key = "quick_picks_section") {
                                QuickPicksSection(
                                    songs = quickPicks,
                                    onSongClick = { song ->
                                        playerViewModel.showAndPlaySong(song, quickPicks, "Quick Picks")
                                    },
                                    onSeeAllClick = {
                                        navController.navigateSafely(Screen.QuickPicksAll.route)
                                    },
                                    currentSongId = currentSongId,
                                    displayMode = quickPicksDisplayMode,
                                    cardSize = 140.dp
                                )
                            }
                        }

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "Smart Mix" || uiState.selectedFilter == "For You") &&
                            uiState.recentMixes.isNotEmpty()
                        ) {
                            item(key = "recent_mixes_header") {
                                SectionHeader(title = "Recent Mixes")
                            }
                            item(key = "recent_mixes_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(items = uiState.recentMixes, key = { index, playlist -> "recent_mix_${playlist.id}_$index" }) { _, playlist ->
                                        RecentMixCardItem(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        val hasLibraryContent = uiState.libraryPlaylists.isNotEmpty() || fromYourLibraryAlbums.isNotEmpty()
                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") && hasLibraryContent) {
                            item(key = "your_library_header") {
                                SectionHeader(
                                    title = "Your Library",
                                    onActionClick = {
                                        navController.navigateSafely(Screen.Library.route)
                                    },
                                    actionLabel = "See All"
                                )
                            }
                            item(key = "your_library_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    itemsIndexed(items = fromYourLibraryAlbums, key = { index, album -> "library_album_${album.browseId}_$index" }) { _, album ->
                                        LibraryAlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                            }
                                        )
                                    }
                                    itemsIndexed(items = uiState.libraryPlaylists, key = { index, playlist -> "library_playlist_${playlist.id}_$index" }) { _, playlist ->
                                        LibraryPlaylistCard(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // New Releases section
                        if (uiState.selectedFilter == "New Releases" && uiState.newReleaseAlbums.isNotEmpty()) {
                            item(key = "new_releases_full_header") {
                                SectionHeader(title = "All New Releases & Singles")
                            }
                            val albums = uiState.newReleaseAlbums
                            val chunkedAlbums = albums.chunked(2)
                            items(items = chunkedAlbums, key = { chunk -> "nr_row_${chunk.first().browseId}" }) { rowAlbums ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowAlbums.forEach { album ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            AlbumCarouselItem(
                                                album = album,
                                                onClick = {
                                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                                }
                                            )
                                        }
                                    }
                                    if (rowAlbums.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") &&
                            uiState.newReleaseAlbums.isNotEmpty()
                        ) {
                            item(key = "new_releases_header") {
                                SectionHeader(
                                    title = "New Releases",
                                    onActionClick = {
                                        exploreViewModel.setSelectedFilter("New Releases")
                                    }
                                )
                            }
                            item(key = "new_releases_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(items = uiState.newReleaseAlbums, key = { album -> "new_release_${album.browseId}" }) { album ->
                                        AlbumCarouselItem(
                                            album = album,
                                            onClick = {
                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Smart Mix Horizontal List for Smart Mix Filter
                        if (uiState.selectedFilter == "Smart Mix" && uiState.recentMixes.isNotEmpty()) {
                            item(key = "smart_mix_header") {
                                SectionHeader(title = "Your Smart Mixes")
                            }
                            item(key = "smart_mix_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    itemsIndexed(items = uiState.recentMixes, key = { index, playlist -> "smart_mix_${playlist.id}_$index" }) { _, playlist ->
                                        RecentMixCardItem(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") {

                            remainingSections.forEachIndexed { index, processed ->
                                val section = processed.section
                                val isSimilar = processed.isSimilar
                                val isBento = processed.isBento

                                if (isBento) {
                                    item(
                                        key = "bento_${section.title}_$index",
                                        contentType = "swipeable_carousel"
                                    ) {
                                        LibrarySwipeableCarousel(section, navController, playerViewModel)
                                    }
                                } else {
                                    item(
                                        key = "home_section_${section.title}_${index}_header",
                                        contentType = "section_header"
                                    ) {
                                        val endpoint = section.endpoint
                                        val onHeaderClick: (() -> Unit)? = endpoint?.browseId?.let { browseId ->
                                            {
                                                when {
                                                    browseId.startsWith("UC") || browseId.startsWith("FEmusic_artist") -> {
                                                        navController.navigateSafely(Screen.ArtistDetail.createRoute(browseId))
                                                    }
                                                    browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("RD") || browseId.startsWith("FEmusic_playlist") -> {
                                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(browseId.removePrefix("VL")))
                                                    }
                                                    browseId.startsWith("MPRE") || browseId.startsWith("FEmusic_album") -> {
                                                        navController.navigateSafely(Screen.AlbumDetail.createRoute(browseId))
                                                    }
                                                    else -> {
                                                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(browseId))
                                                    }
                                                }
                                            }
                                        }
                                        SectionHeader(
                                            title = section.title,
                                            thumbnail = section.thumbnail,
                                            onActionClick = onHeaderClick
                                        )
                                    }
                                    item(
                                        key = "home_section_${section.title}_${index}_carousel",
                                        contentType = "yt_item_carousel"
                                    ) {
                                        if (isSimilar) {
                                            SimilarArtistsCarousel(
                                                artists = section.items.filterIsInstance<ArtistItem>(),
                                                navController = navController,
                                                playerViewModel = playerViewModel
                                            )
                                        } else {
                                            YTItemCarousel(
                                                items = section.items,
                                                navController = navController,
                                                playerViewModel = playerViewModel,
                                                sectionTitle = section.title
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.isContinuationLoading) {
                            item(key = "home_continuation_loader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YTItemCarousel(
    items: List<YTItem>,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    sectionTitle: String
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(items = items, key = { index, item -> "${item.id}_$index" }) { _, item ->
            when (item) {
                is SongItem -> {
                    val songNative = item.toNativeSong()
                    SongCardItem(
                        song = songNative,
                        onClick = {
                            playerViewModel.showAndPlaySong(
                                song = songNative,
                                contextSongs = items.filterIsInstance<SongItem>().map { it.toNativeSong() },
                                queueName = sectionTitle
                            )
                        }
                    )
                }
                is AlbumItem -> {
                    AlbumCarouselItem(
                        album = item,
                        onClick = {
                            navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        }
                    )
                }
                is PlaylistItem -> {
                    PlaylistCardItem(
                        playlist = item,
                        onClick = {
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                        }
                    )
                }
                is ArtistItem -> {
                    ArtistCardItem(
                        artist = item,
                        onClick = {
                            navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        },
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun SongCardItem(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            modifier = Modifier
                .size(136.dp)
                .clip(AbsoluteSmoothCornerShape(22.dp, 80)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val subtitle = if (song.artist.isNotBlank()) "Song • ${song.artist}" else "Song"
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun AnimatedSparklesIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val gradientBrush = remember(colors) {
        Brush.linearGradient(
            colors = listOf(
                colors.primary,
                colors.tertiary
            )
        )
    }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(gradientBrush)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.gemini_ai),
                contentDescription = "Smart Mix Studio",
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun AlbumCarouselItem(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            SmartImage(
                model = album.thumbnail,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AbsoluteSmoothCornerShape(22.dp, 80)),
                contentScale = ContentScale.Crop
            )
            val isSingle = album.releaseType == unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumReleaseType.SINGLE
            val typeLabel = if (isSingle) "Single" else if (album.releaseType == unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumReleaseType.EP) "EP" else "Album"
            Surface(
                shape = ExpressiveSmallShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val artistText = album.artists?.joinToString { it.name }
        val isSingle = album.releaseType == unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumReleaseType.SINGLE
        val type = if (isSingle) "Single" else if (album.releaseType == unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumReleaseType.EP) "EP" else "Album"
        val subtitle = if (!artistText.isNullOrBlank()) "$type • $artistText" else type
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun PlaylistCardItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = playlist.thumbnail,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(136.dp)
                .clip(AbsoluteSmoothCornerShape(22.dp, 80)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val authorName = playlist.author?.name
        val subtitle = if (!authorName.isNullOrBlank()) "Playlist • $authorName" else "Playlist"
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun LibraryPlaylistCard(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val previewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    var playlistSongs by remember(previewSongIds) {
        mutableStateOf<List<Song>?>(if (previewSongIds.isEmpty()) emptyList() else null)
    }
    LaunchedEffect(previewSongIds) {
        if (previewSongIds.isNotEmpty()) {
            playlistSongs = playerViewModel.getSongs(previewSongIds)
        }
    }

    val dominantColor = playlist.coverColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val isDarkTheme = isSystemInDarkTheme()
    val blendedBgColor = remember(dominantColor, cardBgColor, isDarkTheme) {
        val blendFraction = if (isDarkTheme) 0.18f else 0.35f
        androidx.compose.ui.graphics.lerp(cardBgColor, dominantColor, blendFraction)
    }
    
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = blendedBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(ExpressiveSmallShape)
                ) {
                    PlaylistCover(
                        playlist = playlist,
                        playlistSongs = playlistSongs ?: emptyList(),
                        modifier = Modifier.fillMaxSize(),
                        size = 96.dp
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable {
                                playlistSongs?.let { songs ->
                                    if (songs.isNotEmpty()) {
                                        playerViewModel.playSongs(songs, songs.first(), playlist.name)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val countText = if (playlist.displaySongCount != null) {
                            "${playlist.displaySongCount} songs"
                        } else {
                            "${playlist.songIds.size} songs"
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    val sourceLabel = if (playlist.source == "YOUTUBE") "YouTube" else "Library"
                    val badgeBg = if (playlist.source == "YOUTUBE") {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    }
                    val badgeText = if (playlist.source == "YOUTUBE") {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeBg, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(24.dp, 80) }
    val coverShape = remember { AbsoluteSmoothCornerShape(14.dp, 80) }
    val colors = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()

    val blendedBgColor = rememberDominantCardColor(
        imageUrl = album.thumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.18f,
        lightBlendFraction = 0.35f
    )

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = blendedBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                blendedBgColor.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = album.thumbnail,
                    contentDescription = album.title,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(coverShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = album.artists?.joinToString { it.name } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.secondaryContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Album",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentMixCardItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(22.dp, 80) }
    val previewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    var playlistSongs by remember(previewSongIds) {
        mutableStateOf<List<Song>?>(if (previewSongIds.isEmpty()) emptyList() else null)
    }
    LaunchedEffect(previewSongIds) {
        if (previewSongIds.isNotEmpty()) {
            playlistSongs = playerViewModel.getSongs(previewSongIds)
        }
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs ?: emptyList(),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                size = 136.dp
            )
            Surface(
                shape = ExpressiveSmallShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = "Smart Mix",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "Smart Mix",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ExploreTopBar(
    onSettingsClick: () -> Unit,
    onCreateClick: () -> Unit,
    isScrolled: Boolean = false,
) {
    val baseContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val solidTintedColor = remember(baseContainerColor, surfaceColor) {
        Color(
            red = (baseContainerColor.red * 0.45f) + (surfaceColor.red * 0.55f),
            green = (baseContainerColor.green * 0.45f) + (surfaceColor.green * 0.55f),
            blue = (baseContainerColor.blue * 0.45f) + (surfaceColor.blue * 0.55f),
            alpha = 1f
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = solidTintedColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explore",
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 40.sp,
                letterSpacing = 1.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedSparklesIconButton(onClick = onCreateClick)

                FilledIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.settings_top_bar_title),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun SectionHeader(
    title: String,
    thumbnail: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onActionClick != null) {
                    Modifier.clickable(onClick = onActionClick)
                } else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (!thumbnail.isNullOrBlank()) {
                SmartImage(
                    model = thumbnail,
                    contentDescription = title,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GoogleSansRounded
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onActionClick != null) {
            if (actionLabel != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "View More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


@Composable
fun ArtistCardItem(
    artist: ArtistItem,
    onClick: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val artistId = artist.id
    val artistName = artist.title
    val artistThumbnail = artist.thumbnail

    val playEndpoint = remember(artist) {
        artist.shuffleEndpoint 
            ?: artist.radioEndpoint
            ?: artist.playEndpoint
            ?: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                playlistId = "RDAMVM$artistId",
                videoId = null
            )
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            if (!artistThumbnail.isNullOrBlank()) {
                SmartImage(
                    model = artistThumbnail,
                    contentDescription = artistName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_music_note_24),
                        contentDescription = artistName,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            if (playerViewModel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(32.dp)
                        .background(
                            color = colorScheme.primary,
                            shape = CircleShape
                        )
                        .clickable {
                            playerViewModel.playRadio(
                                endpoint = playEndpoint,
                                title = "$artistName Mix",
                                artistName = artistName
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play Artist",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = artistName,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        val subtitle = if (!artist.subscriberCountText.isNullOrBlank()) {
            "Artist • ${artist.subscriberCountText}"
        } else {
            "Artist"
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}



@Composable
fun SimilarArtistsCarousel(
    artists: List<ArtistItem>,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(items = artists, key = { index, artist -> "${artist.id}_$index" }) { _, artist ->
            ArtistCardItem(
                artist = artist,
                onClick = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySwipeableCarousel(
    section: HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    // OPTIMIZED: Removed auto-scroll while(true) loop that ran continuous animateScrollToPage every 4.5s.
    // That loop kept a coroutine active even when Composable not visible, causing jank.
    // Now static pager, user-driven only, with precomputed native songs to avoid repeated toNativeSong()
    val items = remember(section.items) { section.items.take(6) } // reduced from 8 to 6 for perf
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    // Precompute native songs once to avoid mapping inside click lambda repeatedly
    val songItemsNative = remember(items) {
        items.filterIsInstance<SongItem>().map { it.toNativeSong() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = section.title)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val item = items[page]
            LibraryCarouselCard(
                item = item,
                onClick = {
                    when (item) {
                        is SongItem -> {
                            val native = songItemsNative.firstOrNull { it.id == item.id } ?: item.toNativeSong()
                            playerViewModel.showAndPlaySong(
                                native,
                                songItemsNative,
                                section.title
                            )
                        }
                        is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                    }
                }
            )
        }

        // Dot indicator - static width, no animateDpAsState per dot (reduces recomposition)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { page ->
                val isSelected = pagerState.currentPage == page
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (isSelected) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(page) } }
                )
            }
        }
    }
}

@Composable
private fun LibraryCarouselCard(
    item: YTItem,
    onClick: () -> Unit
) {
    val title = when (item) {
        is SongItem -> item.title
        is AlbumItem -> item.title
        is ArtistItem -> item.title
        is PlaylistItem -> item.title
        else -> ""
    }
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name } ?: ""
        is ArtistItem -> "Artist"
        is PlaylistItem -> item.songCountText ?: ""
        else -> ""
    }
    val thumbnail: String? = when (item) {
        is SongItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        is ArtistItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
        else -> null
    }
    val badgeLabel = when (item) {
        is PlaylistItem -> if (item.shuffleEndpoint != null) "MIX" else null
        is AlbumItem -> "ALBUM"
        else -> null
    }

    // Dynamic color: extract dominant color with in-memory LRU cache and background IO dispatch.
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val animatedBgColor = rememberDominantCardColor(
        imageUrl = thumbnail,
        baseColor = colorScheme.surfaceContainer,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.35f,
        lightBlendFraction = 0.52f
    )
    val cardShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val badgeShape = remember { AbsoluteSmoothCornerShape(10.dp, 60) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clickable(onClick = onClick),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedBgColor
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail — right side, fading into the card
            if (!thumbnail.isNullOrBlank()) {
                SmartImage(
                    model = thumbnail,
                    contentDescription = title,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f),
                    contentScale = ContentScale.Crop
                )
            }

            // Left side gradient overlay to ensure text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedBgColor,
                                animatedBgColor,
                                animatedBgColor.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Text content — left side
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (badgeLabel != null) {
                    Surface(
                        shape = badgeShape,
                        color = colorScheme.primaryContainer.copy(alpha = 0.88f)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
}
    }
}

@Composable
fun SmartMixStudioHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val heroGradient = remember(primaryContainer, secondaryContainer, surfaceContainerHigh) {
        Brush.horizontalGradient(
            colors = listOf(
                primaryContainer.copy(alpha = 0.75f),
                secondaryContainer.copy(alpha = 0.85f),
                surfaceContainerHigh
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(28.dp, 80))
            .background(heroGradient)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "STUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Smart Mix Studio",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = GoogleSansRounded
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Craft personalized mixes with AI & Last.fm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Create Mix",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}







