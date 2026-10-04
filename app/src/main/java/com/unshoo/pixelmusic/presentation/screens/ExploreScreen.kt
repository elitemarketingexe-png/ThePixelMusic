@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.feed.FeedAlbum
import com.unshoo.pixelmusic.data.feed.FeedArtist
import com.unshoo.pixelmusic.data.feed.FeedMix
import com.unshoo.pixelmusic.data.feed.FeedQuickTile
import com.unshoo.pixelmusic.data.feed.FeedSpotlight
import com.unshoo.pixelmusic.data.feed.FriendEntry
import com.unshoo.pixelmusic.data.feed.GeneratedTrack
import com.unshoo.pixelmusic.data.feed.ImageDto
import com.unshoo.pixelmusic.data.feed.RecentTrack
import com.unshoo.pixelmusic.data.feed.RecentTrackArtistRef
import com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack
import com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary
import com.unshoo.pixelmusic.data.feed.toSong
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.PlayingWaveBars
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import com.unshoo.pixelmusic.presentation.components.PlaylistBottomSheet
import com.unshoo.pixelmusic.presentation.components.QuickPicksSection
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageCardTargetSize
import com.unshoo.pixelmusic.presentation.components.SongInfoBottomSheet
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely
import com.unshoo.pixelmusic.presentation.utils.itemsUnique
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.FeedUiState
import com.unshoo.pixelmusic.presentation.viewmodel.FeedViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.QuickPicksViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.ui.theme.ShapeCache
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@UnstableApi
@Composable
fun ExploreScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    feedViewModel: FeedViewModel = hiltViewModel(),
    quickPicksViewModel: QuickPicksViewModel = hiltViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel(),
) {
    val sheetState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = sheetState == PlayerSheetState.COLLAPSED) {
        navController.navigateToTopLevelSafely(Screen.Home.route)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isOnline by playerViewModel.isOnline.collectAsStateWithLifecycle()
    val state by feedViewModel.uiState.collectAsStateWithLifecycle()
    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val quickPicksRaw by quickPicksViewModel.quickPicks.collectAsStateWithLifecycle()
    val ytHomeQuickPicks = remember(exploreUiState.homePageSections) {
        exploreUiState.homePageSections
            .firstOrNull { it.title.contains("quick", ignoreCase = true) }
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.filterVideo(true)
            ?.map { it.toNativeSong() }
            .orEmpty()
    }
    val effectiveQuickPicks = remember(quickPicksRaw, ytHomeQuickPicks) {
        if (quickPicksRaw.isNotEmpty()) quickPicksRaw else ytHomeQuickPicks
    }
    val quickPicks = remember(effectiveQuickPicks, isOnline) {
        if (!isOnline) {
            effectiveQuickPicks.filter { com.unshoo.pixelmusic.utils.OfflineAudioResolver.hasOfflineAudio(context, it) }
        } else {
            effectiveQuickPicks
        }
    }
    val categories by quickPicksViewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by quickPicksViewModel.selectedCategory.collectAsStateWithLifecycle()
    val quickPicksDisplayMode by playerViewModel.quickPicksDisplayMode.collectAsStateWithLifecycle()
    val localAlbums by playerViewModel.albumsFlow.collectAsStateWithLifecycle()
    val localArtists by playerViewModel.artistsFlow.collectAsStateWithLifecycle()

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying by remember { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSongId by remember { derivedStateOf { stablePlayerState.currentSong?.id } }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var playlistSheetSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val playlistViewModel: PlaylistViewModel = hiltViewModel()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, feedViewModel, exploreViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                feedViewModel.onVisible()
                exploreViewModel.loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isManualRefreshing by remember { mutableStateOf(false) }
    val isRefreshing = state.isRefreshing || isManualRefreshing

    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    val bottomPadding = if (currentSongId != null) MiniPlayerHeight + 16.dp else 16.dp

    val isYtConnected = state.feedData.isYtConnected
    val isAdvancedExplore = exploreUiState.isAdvancedExploreEnabled
    val rawRegionalSections = if (!isAdvancedExplore && exploreUiState.homePageSections.isNotEmpty()) {
        exploreUiState.homePageSections.take(8)
    } else {
        exploreUiState.homePageSections
    }
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore.value, isAdvancedExplore) {
        if (isAdvancedExplore && shouldLoadMore.value && exploreUiState.homePageContinuation != null && !exploreUiState.isContinuationLoading) {
            exploreViewModel.loadMore()
        }
    }

    val chartTracks = remember(state.feedData.charts, exploreUiState.chartsPage, isOnline) {
        if (!isOnline) {
            emptyList()
        } else if (state.feedData.charts.isNotEmpty()) {
            state.feedData.charts
        } else {
            exploreUiState.chartsPage?.sections?.flatMap { it.items }
                ?.filterIsInstance<SongItem>()
                ?.map { songItem ->
                    YouTubeMusicTrack(
                        videoId = songItem.id,
                        title = songItem.title,
                        artist = songItem.artists.firstOrNull()?.name ?: "Unknown artist",
                        album = songItem.album?.name,
                        artworkUrl = songItem.thumbnail
                    )
                }.orEmpty()
        }
    }

    val newReleases = remember(exploreUiState.newReleaseAlbums, state.feedData.newReleases, isOnline) {
        if (!isOnline) {
            emptyList()
        } else if (exploreUiState.newReleaseAlbums.isNotEmpty()) {
            exploreUiState.newReleaseAlbums
                .distinctBy { it.browseId }
                .map { album ->
                    YouTubePlaylistSummary(
                        id = album.browseId,
                        title = album.title,
                        author = album.artists?.firstOrNull()?.name ?: "Album",
                        artworkUrl = album.thumbnail
                    )
                }
        } else {
            state.feedData.newReleases
        }
    }

    // Albums for you: Remote verified records -> Local album collection
    val albumsForYou = remember(state.feedData.recentAlbums, localAlbums, isOnline) {
        if (isOnline && state.feedData.recentAlbums.isNotEmpty()) {
            state.feedData.recentAlbums.distinctBy { it.browseId ?: it.title }
        } else {
            localAlbums.take(15).map { local ->
                FeedAlbum(
                    title = local.title,
                    artist = local.artist,
                    artworkUrl = local.albumArtUriString,
                    browseId = local.id.toString()
                )
            }
        }
    }

    val regionalSections = remember(rawRegionalSections, isOnline, effectiveQuickPicks, newReleases, albumsForYou) {
        if (!isOnline) {
            emptyList()
        } else {
            rawRegionalSections.filter { section ->
                val title = section.title.lowercase()
                val isNewReleasesShelf = title.contains("new release") || title.contains("new releases") ||
                    title.contains("novedades") || title.contains("release radar") || title.contains("new for you") ||
                    title.contains("new album") || title.contains("latest release")
                val isAlbumsShelf = title.contains("albums for you") || title.contains("recommended albums") ||
                    title.contains("featured albums")

                !title.contains("local") &&
                (!title.contains("quick") || effectiveQuickPicks.isEmpty()) &&
                (!isNewReleasesShelf || newReleases.isEmpty()) &&
                (!isAlbumsShelf || albumsForYou.isEmpty()) &&
                section.items.isNotEmpty()
            }
        }
    }

    // Artists for you: When isYtConnected -> Remote topArtists, otherwise -> Local most played topArtists -> Local library artists
    val artistsForYou = remember(state.feedData.topArtists, exploreUiState.localTopArtists, localArtists, isYtConnected, isOnline) {
        if (isOnline && isYtConnected && state.feedData.topArtists.isNotEmpty()) {
            state.feedData.topArtists
        } else if (exploreUiState.localTopArtists.isNotEmpty()) {
            exploreUiState.localTopArtists
        } else if (localArtists.isNotEmpty()) {
            localArtists.sortedByDescending { it.songCount }.take(10).map { artist ->
                FeedArtist(
                    name = artist.name,
                    browseId = null,
                    artworkUrl = artist.customImageUri ?: artist.imageUrl
                )
            }
        } else emptyList()
    }

    // Jump back in: Remote jumpBackIn (when logged in) -> Local highly rotatory history
    val jumpBackInTracks = remember(state.feedData.jumpBackIn, exploreUiState.localHighlyRotatoryTracks, isYtConnected, isOnline) {
        if (isOnline && isYtConnected && state.feedData.jumpBackIn.isNotEmpty()) {
            state.feedData.jumpBackIn
        } else if (exploreUiState.localHighlyRotatoryTracks.isNotEmpty()) {
            exploreUiState.localHighlyRotatoryTracks
        } else emptyList()
    }

    val spotlight = remember(state.feedData.spotlight, artistsForYou) {
        state.feedData.spotlight ?: artistsForYou.firstOrNull()?.let { artist ->
            FeedSpotlight(
                artistName = artist.name,
                artworkUrl = artist.artworkUrl,
                browseId = artist.browseId,
                description = "Spotlight Artist",
                topTrackTitle = null
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExploreTopBar(
                onSettingsClick = { navController.navigateSafely(Screen.Settings.route) },
                onCreateClick = { navController.navigateSafely(Screen.SmartMix.route) },
                isScrolled = isScrolled
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isManualRefreshing = true
                    feedViewModel.refresh()
                    quickPicksViewModel.refresh(force = true)
                    exploreViewModel.loadData(forceRefresh = true)
                    exploreViewModel.loadChartsIfNeeded(forceRefresh = true)
                    kotlinx.coroutines.delay(1000)
                    isManualRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            val hasFeedContent = quickPicks.isNotEmpty() ||
                regionalSections.isNotEmpty() ||
                artistsForYou.isNotEmpty() ||
                jumpBackInTracks.isNotEmpty() ||
                exploreUiState.libraryPlaylists.isNotEmpty() ||
                (isYtConnected && (
                    state.feedData.quickPicks.isNotEmpty() ||
                    state.feedData.topArtists.isNotEmpty() ||
                    state.feedData.newReleases.isNotEmpty() ||
                    chartTracks.isNotEmpty() ||
                    albumsForYou.isNotEmpty()
                ))

            if (state.isLoading && !hasFeedContent) {
                FeedLoadingSkeleton(contentPadding = PaddingValues(bottom = bottomPadding + paddingValuesParent.calculateBottomPadding()))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = paddingValuesParent.calculateBottomPadding() + bottomPadding + 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // 0. Material 3 Expressive Category / Mood Chips
                    if (categories.size > 1) {
                        item(key = "quick_picks_categories") {
                            ExploreCategoryChipsRow(
                                categories = categories,
                                selectedCategory = selectedCategory,
                                onCategorySelect = { category ->
                                    quickPicksViewModel.setCategory(category)
                                }
                            )
                        }
                    }

                    // 1. Quick Picks Section (RETAINED & WIRED WITH HOME QUICKPICKS)
                    if (quickPicks.isNotEmpty()) {
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

                    // 2. Artist Spotlight Hero Card (shown in normal explore screen even if advance is off)
                    spotlight?.let { spot ->
                        item(key = "feed_spotlight") {
                            SpotlightHeroCard(
                                spotlight = spot,
                                onPlayRadio = {
                                    feedViewModel.playArtistRadio(
                                        FeedArtist(spot.artistName, spot.browseId, spot.artworkUrl),
                                        playerViewModel
                                    )
                                },
                                onOpenArtist = {
                                    navController.navigateSafely(Screen.ArtistDetail.createRoute(spot.browseId ?: spot.artistName))
                                }
                            )
                        }
                    }

                    // 3. Top Artists Carousel (shown in normal explore screen even if advance is off)
                    if (artistsForYou.isNotEmpty()) {
                        item(key = "feed_top_artists") {
                            FeedSectionHeader(
                                title = stringResource(R.string.explore_section_artists_for_you),
                                subtitle = if (state.feedData.topArtists.isNotEmpty()) stringResource(R.string.explore_subtitle_most_replayed_musicians) else stringResource(R.string.explore_subtitle_most_played_artists)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                itemsIndexed(artistsForYou, key = { idx, a -> "artist_${a.name}_$idx" }) { index, artist ->
                                    ArtistAvatarCard(
                                        artist = artist,
                                        isTop = index < 3,
                                        onClick = {
                                            navController.navigateSafely(
                                                Screen.ArtistDetail.createRoute(artist.browseId ?: artist.name)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Feed content (loaded into explore screen when Advanced Explore is ON)
                    if (isAdvancedExplore) {
                        // Taste Tags Strip
                        if (isYtConnected && state.feedData.tasteTags.isNotEmpty()) {
                            item(key = "feed_taste_strip") {
                                TasteStrip(
                                    tags = state.feedData.tasteTags,
                                    onTagClick = { tag ->
                                        navController.navigateSafely(Screen.Search.route)
                                    }
                                )
                            }
                        }

                        // Because you listen to X Section
                        if (isYtConnected) {
                            state.feedData.becauseYouListenTo?.takeIf { it.items.isNotEmpty() }?.let { section ->
                                item(key = "because_you_listen_to") {
                                    FeedSectionHeader(
                                        title = section.title,
                                        subtitle = section.subtitle,
                                        actionText = stringResource(R.string.action_play_all),
                                        actionIcon = Icons.Filled.PlayArrow,
                                        onActionClick = {
                                            feedViewModel.playTracksQueue(section.items, 0, playerViewModel, section.title)
                                        },
                                        onShuffleClick = {
                                            feedViewModel.shuffleTracksQueue(section.items, playerViewModel, section.title)
                                        }
                                    )
                                    FeedMediaRow {
                                        itemsIndexed(section.items, key = { idx, t -> "because_${t.videoId}_$idx" }) { index, track ->
                                            FeedTrackCard(
                                                title = track.title,
                                                subtitle = track.artist,
                                                artworkUrl = track.artworkUrl,
                                                isCurrentPlaying = isPlaying && currentSongId == "youtube_${track.videoId}",
                                                onClick = {
                                                    feedViewModel.playTracksQueue(section.items, index, playerViewModel, section.title)
                                                },
                                                onMoreClick = {
                                                    playerViewModel.selectSongForInfo(track.toSong())
                                                    showSongInfoBottomSheet = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Fresh Finds Section
                        if (isYtConnected && state.feedData.freshFinds.isNotEmpty()) {
                            item(key = "feed_fresh_finds") {
                                FeedSectionHeader(
                                    title = stringResource(R.string.explore_section_fresh_finds),
                                    subtitle = stringResource(R.string.explore_section_fresh_finds_desc),
                                    actionText = stringResource(R.string.action_play_all),
                                    actionIcon = Icons.Filled.PlayArrow,
                                    onActionClick = {
                                        feedViewModel.playTracksQueue(state.feedData.freshFinds, 0, playerViewModel, "Fresh Finds")
                                    },
                                    onShuffleClick = {
                                        feedViewModel.shuffleTracksQueue(state.feedData.freshFinds, playerViewModel, "Fresh Finds")
                                    }
                                )
                                FeedMediaRow {
                                    itemsIndexed(state.feedData.freshFinds, key = { idx, t -> "fresh_${t.videoId}_$idx" }) { index, track ->
                                        FeedTrackCard(
                                            title = track.title,
                                            subtitle = track.artist,
                                            artworkUrl = track.artworkUrl,
                                            badgeText = stringResource(R.string.badge_new),
                                            isCurrentPlaying = isPlaying && currentSongId == "youtube_${track.videoId}",
                                            onClick = {
                                                feedViewModel.playTracksQueue(state.feedData.freshFinds, index, playerViewModel, "Fresh Finds")
                                            },
                                            onMoreClick = {
                                                playerViewModel.selectSongForInfo(track.toSong())
                                                showSongInfoBottomSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Jump Back In Section
                        if (jumpBackInTracks.isNotEmpty()) {
                            item(key = "feed_jump_back_in") {
                                FeedSectionHeader(
                                    title = stringResource(R.string.explore_section_jump_back_in),
                                    subtitle = if (isYtConnected && state.feedData.jumpBackIn.isNotEmpty()) stringResource(R.string.explore_subtitle_recent_history) else stringResource(R.string.explore_subtitle_rotatory_history),
                                    actionText = stringResource(R.string.action_play_all),
                                    actionIcon = Icons.Filled.PlayArrow,
                                    onActionClick = {
                                        val allLocal = jumpBackInTracks.mapNotNull { exploreUiState.localSongs[it.url] }
                                        if (allLocal.isNotEmpty()) {
                                            playerViewModel.playSongs(allLocal, allLocal.first(), queueName = "Jump Back In")
                                        } else {
                                            feedViewModel.playRecentQueue(jumpBackInTracks, 0, playerViewModel)
                                        }
                                    }
                                )
                                FeedMediaRow {
                                    itemsIndexed(jumpBackInTracks, key = { idx, t -> "jump_${t.name}_$idx" }) { index, track ->
                                        RecentTrackCard(
                                            track = track,
                                            onClick = {
                                                val localSong = exploreUiState.localSongs[track.url]
                                                if (localSong != null) {
                                                    val allLocal = jumpBackInTracks.mapNotNull { exploreUiState.localSongs[it.url] }
                                                    playerViewModel.playSongs(allLocal, localSong, queueName = "Jump Back In")
                                                } else {
                                                    feedViewModel.playRecentQueue(jumpBackInTracks, index, playerViewModel)
                                                }
                                            },
                                            onMoreClick = {
                                                val localSong = exploreUiState.localSongs[track.url]
                                                val song = localSong ?: track.toSong()
                                                playerViewModel.selectSongForInfo(song)
                                                showSongInfoBottomSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Mixes to Explore
                        if (isYtConnected && state.feedData.mixes.isNotEmpty()) {
                            item(key = "feed_mixes") {
                                FeedSectionHeader(
                                    title = stringResource(R.string.explore_section_mixes_to_explore),
                                    subtitle = stringResource(R.string.explore_section_mixes_to_explore_desc),
                                    actionText = stringResource(R.string.action_shuffle),
                                    actionIcon = Icons.Filled.Shuffle,
                                    onActionClick = {
                                        state.feedData.mixes.randomOrNull()?.let { feedViewModel.playMix(it, playerViewModel) }
                                    }
                                )
                                FeedMediaRow {
                                    itemsIndexed(state.feedData.mixes.distinctBy { it.seed.videoId }, key = { idx, mix -> "mix_${mix.seed.videoId}_$idx" }) { _, mix ->
                                        FeedPlaylistCard(
                                            title = mix.title,
                                            subtitle = stringResource(R.string.explore_subtitle_artist_radio_station),
                                            artworkUrl = mix.seed.artworkUrl,
                                            onClick = { feedViewModel.playMix(mix, playerViewModel) }
                                        )
                                    }
                                }
                            }
                        }

                        // Heavy Rotation Section
                        if (isYtConnected && state.feedData.heavyRotation.isNotEmpty()) {
                            item(key = "feed_heavy_rotation") {
                                FeedSectionHeader(
                                    title = stringResource(R.string.explore_section_favorites_to_revisit),
                                    subtitle = stringResource(R.string.explore_subtitle_high_affinity_rotation),
                                    actionText = stringResource(R.string.action_play_all),
                                    actionIcon = Icons.Filled.PlayArrow,
                                    onActionClick = {
                                        feedViewModel.playGeneratedQueue(state.feedData.heavyRotation, 0, playerViewModel, "Heavy Rotation")
                                    }
                                )
                                FeedMediaRow {
                                    itemsIndexed(state.feedData.heavyRotation, key = { idx, t -> "heavy_${t.key}_$idx" }) { index, track ->
                                        FeedTrackCard(
                                            title = track.name,
                                            subtitle = track.artist,
                                            artworkUrl = track.artworkUrl,
                                            isCurrentPlaying = isPlaying && currentSongId == "youtube_${track.toSong().youtubeId}",
                                            onClick = {
                                                feedViewModel.playGeneratedQueue(state.feedData.heavyRotation, index, playerViewModel, "Heavy Rotation")
                                            },
                                            onMoreClick = {
                                                playerViewModel.selectSongForInfo(track.toSong())
                                                showSongInfoBottomSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Albums for You (Real verified records & local album collection)
                        if (isYtConnected && albumsForYou.isNotEmpty()) {
                            item(key = "feed_albums") {
                                FeedSectionHeader(
                                    title = stringResource(R.string.explore_section_albums_for_you),
                                    subtitle = if (state.feedData.recentAlbums.isNotEmpty()) stringResource(R.string.explore_subtitle_verified_records) else stringResource(R.string.explore_subtitle_local_albums)
                                )
                                FeedMediaRow {
                                    itemsIndexed(albumsForYou.distinctBy { it.browseId ?: it.title }, key = { idx, album -> "album_${album.browseId ?: album.title}_$idx" }) { _, album ->
                                        FeedPlaylistCard(
                                            title = album.title,
                                            subtitle = album.artist,
                                            artworkUrl = album.artworkUrl,
                                            onClick = {
                                                val id = album.browseId
                                                if (!id.isNullOrBlank()) {
                                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(id))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Your Library Section (positioned right after Artists for you)
                    val libraryPlaylists = exploreUiState.libraryPlaylists
                    if (libraryPlaylists.isNotEmpty()) {
                        item(key = "feed_your_library") {
                            FeedSectionHeader(
                                title = stringResource(R.string.explore_section_your_library),
                                subtitle = stringResource(R.string.explore_section_your_library_desc),
                                actionText = stringResource(R.string.action_see_all),
                                actionIcon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                onActionClick = { navController.navigateToTopLevelSafely(Screen.Library.route) }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                itemsIndexed(libraryPlaylists, key = { idx, p -> "lib_${p.id}_$idx" }) { _, playlist ->
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

                    // 13. Trending & Charts Section (Regional & Taste-boosted)
                    if (isYtConnected && chartTracks.isNotEmpty()) {
                        item(key = "feed_charts") {
                            FeedSectionHeader(
                                title = stringResource(R.string.explore_section_trending_now),
                                subtitle = stringResource(R.string.explore_section_trending_now_desc),
                                actionText = stringResource(R.string.action_play_all),
                                actionIcon = Icons.Filled.PlayArrow,
                                onActionClick = {
                                    feedViewModel.playTracksQueue(chartTracks, 0, playerViewModel, "Top Charts")
                                },
                                onShuffleClick = {
                                    feedViewModel.shuffleTracksQueue(chartTracks, playerViewModel, "Top Charts")
                                }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(top = 10.dp)
                            ) {
                                itemsIndexed(chartTracks.take(15), key = { idx, t -> "chart_${t.videoId}_$idx" }) { index, track ->
                                    ChartTrackCard(
                                        rank = index + 1,
                                        track = track,
                                        isCurrentPlaying = isPlaying && currentSongId == "youtube_${track.videoId}",
                                        onClick = {
                                            feedViewModel.playTracksQueue(chartTracks, index, playerViewModel, "Top Charts")
                                        },
                                        onMoreClick = {
                                            playerViewModel.selectSongForInfo(track.toSong())
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 14. New Releases Section (Regional & Taste-boosted) OR Recently Added Fallback
                    if (isYtConnected && newReleases.isNotEmpty()) {
                        item(key = "feed_new_releases") {
                            FeedSectionHeader(
                                title = stringResource(R.string.explore_section_new_releases),
                                subtitle = stringResource(R.string.explore_section_new_releases_desc),
                                actionText = stringResource(R.string.action_see_all),
                                actionIcon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                onActionClick = {
                                    navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute("new_releases"))
                                }
                            )
                            FeedMediaRow {
                                itemsIndexed(newReleases.distinctBy { it.id }, key = { idx, release -> "release_${release.id}_$idx" }) { _, release ->
                                    FeedPlaylistCard(
                                        title = release.title,
                                        subtitle = release.author ?: stringResource(R.string.unknown_album),
                                        artworkUrl = release.artworkUrl,
                                        badgeText = stringResource(R.string.badge_new),
                                        onClick = {
                                            if (release.id.startsWith("MPRE") || release.id.startsWith("FEmusic_album")) {
                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(release.id))
                                            } else if (release.id.startsWith("VL") || release.id.startsWith("PL") || release.id.startsWith("RD")) {
                                                navController.navigateSafely(Screen.FeedPlaylistDetail.createRoute(release.id))
                                            } else {
                                                val song = com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack(
                                                    videoId = release.id,
                                                    title = release.title,
                                                    artist = release.author ?: "Artist",
                                                    artworkUrl = release.artworkUrl
                                                ).toSong()
                                                playerViewModel.showAndPlaySong(song, listOf(song), "New Releases")
                                            }
                                        },
                                        onMoreClick = {
                                            val song = com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack(
                                                videoId = release.id,
                                                title = release.title,
                                                artist = release.author ?: "Artist",
                                                artworkUrl = release.artworkUrl
                                            ).toSong()
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    } else if (isYtConnected && exploreUiState.localRecentlyAddedSongs.isNotEmpty()) {
                        val recentSongs = exploreUiState.localRecentlyAddedSongs
                        item(key = "feed_recently_added") {
                            FeedSectionHeader(
                                title = stringResource(R.string.explore_section_recently_added),
                                subtitle = stringResource(R.string.explore_subtitle_recently_added_desc),
                                actionText = stringResource(R.string.action_play_all),
                                actionIcon = Icons.Filled.PlayArrow,
                                onActionClick = {
                                    playerViewModel.playSongs(recentSongs, recentSongs.first(), queueName = "Recently Added")
                                },
                                onShuffleClick = {
                                    playerViewModel.playSongsShuffled(recentSongs, queueName = "Recently Added")
                                }
                            )
                            FeedMediaRow {
                                itemsIndexed(recentSongs, key = { idx, song -> "recent_added_${song.id}_$idx" }) { index, song ->
                                    FeedTrackCard(
                                        title = song.title,
                                        subtitle = song.displayArtist,
                                        artworkUrl = song.albumArtUriString,
                                        badgeText = stringResource(R.string.badge_recent),
                                        isCurrentPlaying = isPlaying && currentSongId == song.id,
                                        onClick = {
                                            playerViewModel.showAndPlaySong(song, recentSongs, "Recently Added")
                                        },
                                        onMoreClick = {
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 15. Advanced Explore / Homepage Sections (Similar Artists, Listen Again, Bento Mixes, Categories)
                    if (regionalSections.isNotEmpty()) {
                        itemsIndexed(regionalSections, key = { idx, s -> "regional_section_${s.title}_$idx" }) { idx, section ->
                            val isSimilar = section.title.startsWith("Similar to", ignoreCase = true) ||
                                section.title.contains("Fans also like", ignoreCase = true) ||
                                section.title.contains("Similar", ignoreCase = true) ||
                                (section.items.isNotEmpty() && section.items.all { it is ArtistItem })
                            val isBento = !isSimilar && isBentoSection(section.title, section.items.size)

                            when {
                                isBento -> {
                                    LibrarySwipeableCarousel(
                                        section = section,
                                        navController = navController,
                                        playerViewModel = playerViewModel,
                                        feedViewModel = feedViewModel
                                    )
                                }
                                isSimilar -> {
                                    SimilarArtistsCarousel(
                                        section = section,
                                        navController = navController
                                    )
                                }
                                else -> {
                                    RegionalExploreSection(
                                        section = section,
                                        navController = navController,
                                        playerViewModel = playerViewModel,
                                        feedViewModel = feedViewModel,
                                        currentSongId = currentSongId,
                                        isPlaying = isPlaying,
                                        onSongMoreClick = { song ->
                                            playerViewModel.selectSongForInfo(song)
                                            showSongInfoBottomSheet = true
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (exploreUiState.isContinuationLoading) {
                        item(key = "home_continuation_loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                playerViewModel = playerViewModel,
                isFavorite = isFavorite,
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
                    playlistSheetSongs = listOf(currentSong)
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    if (currentSong.albumId != 0L) {
                        navController.navigateSafely(Screen.AlbumDetail.createRoute(currentSong.albumId))
                    }
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    val target = currentSong.artists.firstOrNull()?.id?.takeIf { it != 0L && it != -1L }?.toString()
                        ?: currentSong.artists.firstOrNull()?.name
                        ?: currentSong.artist
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(target))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtistById = { artistId ->
                    if (artistId.isNotBlank()) {
                        navController.navigateSafely(Screen.ArtistDetail.createRoute(artistId))
                    }
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {},
                onEditSong = { newTitle, newArtist, newAlbum, newAlbumArtist, newComposer, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newAlbumArtist,
                        newComposer,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                    showSongInfoBottomSheet = false
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
                removeFromListTrigger = {}
            )
        }
    }

    if (showPlaylistBottomSheet) {
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        PlaylistBottomSheet(
            playlistUiState = playlistUiState,
            songs = playlistSheetSongs,
            onDismiss = { showPlaylistBottomSheet = false },
            bottomBarHeight = 0.dp,
            playerViewModel = playerViewModel,
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
                text = stringResource(R.string.tab_explore),
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
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
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
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
private fun QuickTilesGrid(
    tiles: List<FeedQuickTile>,
    onTileClick: (FeedQuickTile) -> Unit
) {
    val rows = remember(tiles) { tiles.take(4).chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { tile ->
                    QuickTileCard(
                        tile = tile,
                        modifier = Modifier.weight(1f),
                        onClick = { onTileClick(tile) }
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickTileCard(
    tile: FeedQuickTile,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(18.dp, 75) }
    Card(
        modifier = modifier
            .height(64.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (tile.isLiked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .background(
                        if (tile.isLiked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tile.isLiked) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!tile.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = tile.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (tile.collection == "new_releases") {
                    Icon(
                        Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.subtitle ?: "Mix",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TasteStrip(
    tags: List<String>,
    onTagClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        FeedSectionHeader(
            title = "Your sound",
            subtitle = "Tap a vibe to explore"
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            itemsUnique(tags, key = { it }) { tag ->
                Surface(
                    onClick = { onTagClick(tag) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = tag.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedSectionHeader(
    title: String,
    subtitle: String? = null,
    avatarUrl: String? = null,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
    onShuffleClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (onActionClick != null) Modifier.clickable(onClick = onActionClick)
                        else Modifier
                    )
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onActionClick != null && actionText == null) Modifier.clickable(onClick = onActionClick)
                    else Modifier
                )
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = GoogleSansRounded,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onShuffleClick != null) {
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onShuffleClick()
                },
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(17.dp))
            }
        }

        if (actionText != null && onActionClick != null) {
            val isSeeAll = actionText.equals("See all", ignoreCase = true) || actionText.equals("See All", ignoreCase = true)
            if (isSeeAll) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onActionClick()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        actionText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (actionIcon != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            actionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onActionClick()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(actionText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        } else if (onActionClick != null && actionText == null) {
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onActionClick()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = "Navigate to $title",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeedMediaRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 10.dp),
        content = content
    )
}

@Composable
private fun FeedTrackCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    isCurrentPlaying: Boolean = false,
    badgeText: String? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val cardShape = remember { AbsoluteSmoothCornerShape(18.dp, 75) }

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(modifier = Modifier.size(148.dp)) {
            Surface(
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    SmartImage(
                        model = artworkUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        shape = cardShape,
                        targetSize = SmartImageCardTargetSize,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (badgeText != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isCurrentPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingWaveBars(waveColor = Color.White, containerColor = Color.Transparent)
                        }
                    }
                }
            }

            Surface(
                onClick = onMoreClick,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FeedPlaylistCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    badgeText: String? = null,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val cardShape = remember { AbsoluteSmoothCornerShape(18.dp, 75) }

    Column(
        modifier = Modifier
            .width(156.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(modifier = Modifier.size(156.dp)) {
            Surface(
                shape = cardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    if (badgeText != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (onMoreClick != null) {
                Surface(
                    onClick = onMoreClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play $title",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentTrackCard(
    track: RecentTrack,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val ago = remember(track.date?.uts) {
        val uts = track.date?.uts?.toLongOrNull()
        if (uts != null && uts > 0L) {
            val diffSec = (System.currentTimeMillis() / 1000L) - uts
            when {
                diffSec < 60 -> "just now"
                diffSec < 3600 -> "${diffSec / 60}m ago"
                diffSec < 86400 -> "${diffSec / 3600}h ago"
                diffSec < 604800 -> "${diffSec / 86400}d ago"
                else -> "${diffSec / 604800}w ago"
            }
        } else null
    }

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(modifier = Modifier.size(148.dp)) {
            Surface(
                shape = AbsoluteSmoothCornerShape(18.dp, 75),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (ago != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                ago,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (onMoreClick != null) {
                Surface(
                    onClick = onMoreClick,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = track.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChartTrackCard(
    rank: Int,
    track: YouTubeMusicTrack,
    isCurrentPlaying: Boolean = false,
    onClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null
) {
    val isTop3 = rank <= 3
    val haptics = LocalHapticFeedback.current

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = AbsoluteSmoothCornerShape(18.dp, 75),
        color = if (isTop3) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isTop3) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .width(280.dp)
            .height(72.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = String.format(Locale.getDefault(), "%02d", rank),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Black,
                color = if (isTop3) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(36.dp)
            )

            Box(modifier = Modifier.size(52.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayingWaveBars(waveColor = Color.White, containerColor = Color.Transparent)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightHeroCard(
    spotlight: FeedSpotlight,
    onPlayRadio: () -> Unit,
    onOpenArtist: () -> Unit
) {
    Card(
        shape = AbsoluteSmoothCornerShape(26.dp, 80),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSystemInDarkTheme()) 2.dp else 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (!spotlight.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = spotlight.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.22f)
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                stringResource(R.string.explore_artist_spotlight_badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            onClick = onOpenArtist,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(72.dp)
                        ) {
                            if (!spotlight.artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = spotlight.artworkUrl,
                                    contentDescription = spotlight.artistName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = spotlight.artistName,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = GoogleSansRounded
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            spotlight.topTrackTitle?.takeIf(String::isNotBlank)?.let { title ->
                                Text(
                                    text = stringResource(R.string.explore_top_track_prefix, title),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPlayRadio,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.explore_btn_artist_radio), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        FilledTonalButton(
                            onClick = onOpenArtist,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text(stringResource(R.string.explore_btn_view_artist), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAvatarCard(
    artist: FeedArtist,
    isTop: Boolean = false,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isTop) androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                ) else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.size(84.dp)
            ) {
                if (!artist.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = artist.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (isTop) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(
                        Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(4.dp).size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun FriendAvatarCard(friend: FriendEntry) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(60.dp)
        ) {
            if (!friend.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = friend.avatarUrl,
                    contentDescription = friend.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = friend.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = friend.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeedFooter(lastUpdatedMillis: Long) {
    val updatedText = remember(lastUpdatedMillis) {
        if (lastUpdatedMillis <= 0L) "Updated recently"
        else {
            val diffMinutes = (System.currentTimeMillis() - lastUpdatedMillis) / 60000L
            when {
                diffMinutes < 1 -> "Updated just now"
                diffMinutes < 60 -> "Updated $diffMinutes mins ago"
                else -> "Updated today"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "PixelMusic • Powered by LastWave Feed Engine",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Text(
            text = updatedText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeedLoadingSkeleton(contentPadding: PaddingValues) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(180.dp)
                    .clip(AbsoluteSmoothCornerShape(26.dp, 75))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
            )
        }
        item {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clip(AbsoluteSmoothCornerShape(18.dp, 75))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
                            )
                        }
                    }
                }
            }
        }
        items(3) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(160.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(148.dp)
                                    .clip(AbsoluteSmoothCornerShape(18.dp, 75))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
                            )
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = pulse))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedSparklesIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Smart Mix",
            modifier = Modifier.size(20.dp)
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

    val cardShape = remember { AbsoluteSmoothCornerShape(22.dp, 80) }
    val thumbShape = remember { AbsoluteSmoothCornerShape(14.dp, 80) }

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
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
                        .clip(thumbShape)
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

private fun isBentoSection(title: String, itemSize: Int): Boolean {
    val t = title.lowercase()
    return itemSize >= 4 && (
        t.contains("featured") ||
        t.contains("supermix") ||
        t.contains("curated") ||
        t.contains("mixed for you") ||
        t.contains("forgotten favorites") ||
        t.contains("forgotten")
    )
}

@Composable
private fun SimilarArtistsCarousel(
    section: HomePage.Section,
    navController: NavController
) {
    val artists = remember(section.items) { section.items.filterIsInstance<ArtistItem>() }
    if (artists.isEmpty()) return

    FeedSectionHeader(
        title = section.title,
        subtitle = section.label ?: "Similar artists & recommendations"
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        itemsIndexed(artists, key = { idx, artist -> "sim_${section.title}_${artist.id}_$idx" }) { _, artist ->
            ArtistAvatarCard(
                artist = FeedArtist(
                    name = artist.title,
                    browseId = artist.id,
                    artworkUrl = artist.thumbnail
                ),
                isTop = false,
                onClick = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                }
            )
        }
    }
}

@Composable
private fun LibrarySwipeableCarousel(
    section: HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    feedViewModel: FeedViewModel
) {
    val items = remember(section.items) { section.items.take(6) }
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    val songItems = remember(items) { items.filterIsInstance<SongItem>() }
    val songTracks = remember(songItems) {
        songItems.map { s ->
            YouTubeMusicTrack(
                videoId = s.id,
                title = s.title,
                artist = s.artists.firstOrNull()?.name ?: "Unknown artist",
                album = s.album?.name,
                artworkUrl = s.thumbnail
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FeedSectionHeader(
            title = section.title,
            subtitle = section.label ?: "Curated mix for your taste",
            actionText = if (songTracks.isNotEmpty()) "Play all" else null,
            actionIcon = if (songTracks.isNotEmpty()) Icons.Filled.PlayArrow else null,
            onActionClick = if (songTracks.isNotEmpty()) {
                { feedViewModel.playTracksQueue(songTracks, 0, playerViewModel, section.title) }
            } else null
        )

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
                            val track = songTracks.firstOrNull { it.videoId == item.id } ?: YouTubeMusicTrack(
                                videoId = item.id,
                                title = item.title,
                                artist = item.artists.firstOrNull()?.name ?: "Unknown artist",
                                album = item.album?.name,
                                artworkUrl = item.thumbnail
                            )
                            val idx = songTracks.indexOfFirst { it.videoId == item.id }
                            if (idx >= 0) {
                                feedViewModel.playTracksQueue(songTracks, idx, playerViewModel, section.title)
                            } else {
                                playerViewModel.showAndPlaySong(track.toSong(), listOf(track.toSong()), section.title)
                            }
                        }
                        is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                    }
                }
            )
        }

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
    }
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name } ?: ""
        is ArtistItem -> "Artist"
        is PlaylistItem -> item.songCountText ?: ""
    }
    val thumbnail: String? = when (item) {
        is SongItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        is ArtistItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
    }
    val badgeLabel = when (item) {
        is PlaylistItem -> if (item.shuffleEndpoint != null) "MIX" else null
        is AlbumItem -> "ALBUM"
        else -> null
    }

    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val animatedBgColor = rememberDominantCardColor(
        imageUrl = thumbnail,
        baseColor = colorScheme.surfaceContainer,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.35f,
        lightBlendFraction = 0.52f
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clickable(onClick = onClick),
        shape = ShapeCache.smooth28,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedBgColor
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
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
                        shape = ShapeCache.smooth10,
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
private fun RegionalExploreSection(
    section: HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    feedViewModel: FeedViewModel,
    currentSongId: String?,
    isPlaying: Boolean,
    onSongMoreClick: (Song) -> Unit = {}
) {
    val songs = remember(section.items) { section.items.filterIsInstance<SongItem>() }
    val songTracks = remember(songs) {
        songs.map { s ->
            YouTubeMusicTrack(
                videoId = s.id,
                title = s.title,
                artist = s.artists.firstOrNull()?.name ?: "Unknown artist",
                album = s.album?.name,
                artworkUrl = s.thumbnail
            )
        }
    }

    val subtitle = remember(section.title, section.label) {
        if (!section.label.isNullOrBlank()) {
            section.label
        } else {
            val t = section.title.lowercase()
            when {
                t.contains("listen again") -> "Pick up where you left off"
                t.contains("forgotten") -> "Rediscover previous favorites"
                t.contains("mixed for you") || t.contains("mix") -> "Personalized mixes tailored for you"
                t.contains("quick") -> "Fast picks based on your recent activity"
                else -> null
            }
        }
    }

    val navigationAction: (() -> Unit)? = remember(section.endpoint) {
        section.endpoint?.browseId?.let { browseId ->
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
    }

    val hasSongs = songTracks.isNotEmpty()
    FeedSectionHeader(
        title = section.title,
        subtitle = subtitle,
        avatarUrl = section.thumbnail,
        actionText = if (hasSongs) "Play all" else null,
        actionIcon = if (hasSongs) Icons.Filled.PlayArrow else null,
        onActionClick = if (hasSongs) {
            { feedViewModel.playTracksQueue(songTracks, 0, playerViewModel, section.title) }
        } else {
            navigationAction
        },
        onShuffleClick = if (hasSongs && songTracks.size > 1) {
            { feedViewModel.shuffleTracksQueue(songTracks, playerViewModel, section.title) }
        } else null
    )

    val isVideoSection = remember(section.title) {
        section.title.contains("video", ignoreCase = true)
    }
    val filteredItems = remember(section.items, isVideoSection) {
        if (isVideoSection) section.items else section.items.filterVideo(true)
    }

    FeedMediaRow {
        itemsIndexed(filteredItems, key = { idx, item -> "regional_${section.title}_${item.id}_$idx" }) { index, item ->
            when (item) {
                is SongItem -> {
                    val track = songTracks.firstOrNull { it.videoId == item.id } ?: YouTubeMusicTrack(
                        videoId = item.id,
                        title = item.title,
                        artist = item.artists.firstOrNull()?.name ?: "Unknown artist",
                        album = item.album?.name,
                        artworkUrl = item.thumbnail
                    )
                    val songIdx = songTracks.indexOfFirst { it.videoId == item.id }
                    FeedTrackCard(
                        title = track.title,
                        subtitle = track.artist,
                        artworkUrl = track.artworkUrl,
                        isCurrentPlaying = isPlaying && currentSongId == "youtube_${track.videoId}",
                        onClick = {
                            if (songIdx >= 0) {
                                feedViewModel.playTracksQueue(songTracks, songIdx, playerViewModel, section.title)
                            } else {
                                playerViewModel.showAndPlaySong(track.toSong(), listOf(track.toSong()), section.title)
                            }
                        },
                        onMoreClick = {
                            onSongMoreClick(track.toSong())
                        }
                    )
                }
                is AlbumItem -> {
                    FeedPlaylistCard(
                        title = item.title,
                        subtitle = item.artists?.firstOrNull()?.name ?: "Album",
                        artworkUrl = item.thumbnail,
                        onClick = {
                            navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        }
                    )
                }
                is PlaylistItem -> {
                    FeedPlaylistCard(
                        title = item.title,
                        subtitle = item.author?.name ?: "Playlist",
                        artworkUrl = item.thumbnail,
                        onClick = {
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                        }
                    )
                }
                is ArtistItem -> {
                    ArtistAvatarCard(
                        artist = FeedArtist(item.title, item.id, item.thumbnail),
                        isTop = false,
                        onClick = {
                            navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreCategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it }) { category ->
            val isSelected = category.equals(selectedCategory, ignoreCase = true)
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "cat_container"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "cat_content"
            )

            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCategorySelect(category)
                },
                shape = CircleShape,
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = if (isSelected) 3.dp else 0.dp,
                modifier = Modifier
                    .defaultMinSize(minHeight = 38.dp)
                    .animateContentSize()
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatCategoryTitle(category),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun formatCategoryTitle(category: String): String {
    return when (category.lowercase(java.util.Locale.ROOT).trim()) {
        "all" -> stringResource(R.string.explore_chip_all)
        "local" -> stringResource(R.string.explore_chip_local)
        "podcasts" -> stringResource(R.string.explore_chip_podcasts)
        "feel good" -> stringResource(R.string.explore_chip_feel_good)
        "energize" -> stringResource(R.string.explore_chip_energize)
        "relax" -> stringResource(R.string.explore_chip_relax)
        "workout" -> stringResource(R.string.explore_chip_workout)
        "commute" -> stringResource(R.string.explore_chip_commute)
        "focus" -> stringResource(R.string.explore_chip_focus)
        else -> category
    }
}

