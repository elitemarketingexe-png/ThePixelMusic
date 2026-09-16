package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

private val QuickPicksPillHeight = 56.dp
private val QuickPicksPillSpacing = 8.dp
private const val QuickPicksPillsPerColumn = 3
private const val QuickPicksLimit = 48
private val QuickPicksPillArtSize = 36.dp
private val QuickPicksWidthSteps = listOf(148.dp, 166.dp, 184.dp, 202.dp, 220.dp)

private data class QuickPicksPillCell(val song: Song, val width: Dp)
private data class QuickPicksPillRow(val pills: List<QuickPicksPillCell>, val contentWidth: Dp)

@Composable
fun QuickPicksSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    currentSongId: String? = null,
    displayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.LIST,
    cardSize: Dp = 140.dp,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val visible = remember(songs) {
        val count = (songs.size / 3) * 3
        songs.take(count.coerceAtMost(QuickPicksLimit))
    }
    val rows = remember(visible) { buildQuickPickRows(visible) }
    val scrollState = rememberScrollState()
    val actualRowsCount = rows.size
    val sectionHeight = if (actualRowsCount > 0) {
        QuickPicksPillHeight * actualRowsCount + QuickPicksPillSpacing * (actualRowsCount - 1)
    } else 0.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Picks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp)
            )
            if (onSeeAllClick != null) {
                FilledIconButton(
                    modifier = Modifier
                        .height(40.dp)
                        .width(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    onClick = onSeeAllClick
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "See all quick picks",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        if (displayMode == QuickPicksDisplayMode.CARD) {
            val lazyListState = rememberLazyListState()
            val limitSongs = remember(songs) { songs.take(20) }
            
            // Use snapshotFlow instead of LaunchedEffect(isScrollInProgress) so the
            // coroutine doesn't restart on every user touch — it just waits inside.
            LaunchedEffect(limitSongs) {
                if (limitSongs.isEmpty()) return@LaunchedEffect
                while (isActive) {
                    // Wait until the user is not scrolling before auto-advancing
                    snapshotFlow { lazyListState.isScrollInProgress }
                        .filter { !it }
                        .first()
                    delay(2500)
                    // Re-check after delay in case user started scrolling again
                    if (isActive && !lazyListState.isScrollInProgress) {
                        val nextIndex = (lazyListState.firstVisibleItemIndex + 1) % limitSongs.size
                        lazyListState.animateScrollToItem(nextIndex)
                    }
                }
            }

            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(limitSongs, key = { it.id }) { song ->
                    QuickPickPortraitCard(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onSongClick(song) },
                        modifier = Modifier
                            .width(cardSize)
                            .height(cardSize)
                            .graphicsLayer {
                                val layoutInfo = lazyListState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                val itemInfo = visibleItems.firstOrNull { it.key == song.id }
                                if (itemInfo != null) {
                                    val focalPoint = layoutInfo.viewportStartOffset + 16.dp.toPx()
                                    val distanceFromStart = (itemInfo.offset.toFloat() - focalPoint).absoluteValue
                                    val maxDistance = (cardSize + 8.dp).toPx()
                                    val fraction = (distanceFromStart / maxDistance).coerceIn(0f, 1f)
                                    val scale = 0.86f + (1f - 0.86f) * (1f - fraction)
                                    scaleX = scale
                                    scaleY = scale
                                    alpha = 0.7f + (1f - 0.7f) * (1f - fraction)
                                } else {
                                    scaleX = 0.86f
                                    scaleY = 0.86f
                                    alpha = 0.7f
                                }
                            }
                    )
                }
            }
        } else if (displayMode == QuickPicksDisplayMode.CARD_CLASSIC) {
            val limitSongs = remember(songs) { songs.take(20) }
            val lazyListState = rememberLazyListState()
            val context = LocalContext.current
            
            // Query system reduced motion (animation scale)
            val isReducedMotion = remember(context) {
                try {
                    android.provider.Settings.Global.getFloat(
                        context.contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f
                    ) == 0f
                } catch (e: Exception) {
                    false
                }
            }

            // Same snapshotFlow-based fix as CARD mode above
            LaunchedEffect(limitSongs) {
                if (limitSongs.isEmpty()) return@LaunchedEffect
                while (isActive) {
                    snapshotFlow { lazyListState.isScrollInProgress }
                        .filter { !it }
                        .first()
                    delay(2500)
                    if (isActive && !lazyListState.isScrollInProgress) {
                        val nextIndex = (lazyListState.firstVisibleItemIndex + 1) % limitSongs.size
                        if (isReducedMotion) {
                            lazyListState.scrollToItem(nextIndex)
                        } else {
                            lazyListState.animateScrollToItem(nextIndex)
                        }
                    }
                }
            }

            val cardShape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(limitSongs, key = { it.id }) { song ->
                    Column(
                        modifier = Modifier
                            .width(cardSize)
                            .clickable { onSongClick(song) }
                    ) {
                        Card(
                            modifier = Modifier
                                .size(cardSize)
                                .graphicsLayer {
                                    if (isReducedMotion) {
                                        scaleX = 1f
                                        scaleY = 1f
                                        alpha = 1f
                                    } else {
                                        val layoutInfo = lazyListState.layoutInfo
                                        val visibleItems = layoutInfo.visibleItemsInfo
                                        val itemInfo = visibleItems.firstOrNull { it.key == song.id }
                                        if (itemInfo != null) {
                                            val focalPoint = layoutInfo.viewportStartOffset + 16.dp.toPx()
                                            val distanceFromStart = (itemInfo.offset.toFloat() - focalPoint).absoluteValue
                                            val maxDistance = (cardSize + 8.dp).toPx()
                                            val fraction = (distanceFromStart / maxDistance).coerceIn(0f, 1f)
                                            val scale = 0.9f + (1f - 0.9f) * (1f - fraction)
                                            scaleX = scale
                                            scaleY = scale
                                            alpha = 0.8f + (1f - 0.8f) * (1f - fraction)
                                        } else {
                                            scaleX = 0.9f
                                            scaleY = 0.9f
                                            alpha = 0.8f
                                        }
                                    }
                                },
                            shape = cardShape,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SmartImage(
                                    model = song.albumArtUriString,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                val isPlaying = song.id == currentSongId
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EqualizerAnimation(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(18.dp),
                                            color = Color.White,
                                            barCount = 3
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(7.dp))
                        
                        // Metadata details below card
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = if (song.id == currentSongId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else if (displayMode == QuickPicksDisplayMode.UNCONTAINED) {
            val limitSongs = remember(songs) { songs.take(20) }
            val lazyListState = rememberLazyListState()
            
            LazyRow(
                state = lazyListState,
                contentPadding = PaddingValues(start = 16.dp, end = 76.dp), // Leaves next item cut off for standard uncontained look
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(limitSongs, key = { it.id }) { song ->
                    val uncontainedCardSize = 150.dp
                    Column(
                        modifier = Modifier
                            .width(uncontainedCardSize)
                            .clickable { onSongClick(song) }
                    ) {
                        Card(
                            modifier = Modifier
                                .size(uncontainedCardSize)
                                .graphicsLayer {
                                    val layoutInfo = lazyListState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    val itemInfo = visibleItems.firstOrNull { it.key == song.id }
                                    if (itemInfo != null) {
                                        val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                                        val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                        val fraction = (itemCenter / viewportWidth.toFloat()).coerceIn(0f, 1f)
                                        
                                        // dynamic Material 3 uncontained scale
                                        val scale = if (fraction > 0.6f) {
                                            1f - (fraction - 0.6f) * 0.3f
                                        } else {
                                            1f
                                        }
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 0.7f + (1f - 0.7f) * scale
                                        
                                        // Dynamic Material 3 shape morphing: squircle corner sizes change on scroll position
                                        val currentCorner = if (fraction > 0.5f) {
                                            val morphProgress = (fraction - 0.5f) * 2f
                                            24.dp + (56.dp - 24.dp) * morphProgress.coerceIn(0f, 1f)
                                        } else {
                                            24.dp
                                        }
                                        shape = AbsoluteSmoothCornerShape(currentCorner, 80)
                                        clip = true
                                    } else {
                                        scaleX = 0.88f
                                        scaleY = 0.88f
                                        alpha = 0.7f
                                        shape = AbsoluteSmoothCornerShape(56.dp, 80)
                                        clip = true
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                SmartImage(
                                    model = song.albumArtUriString,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                val isPlaying = song.id == currentSongId
                                if (isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        EqualizerAnimation(
                                            modifier = Modifier
                                                .width(24.dp)
                                                .height(18.dp),
                                            color = Color.White,
                                            barCount = 3
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(7.dp))
                        
                        // Metadata details below card
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                color = if (song.id == currentSongId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .horizontalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing)
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        row.pills.forEach { cell ->
                            QuickPickPill(
                                song = cell.song,
                                width = cell.width,
                                isPlaying = cell.song.id == currentSongId,
                                onClick = { onSongClick(cell.song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 4
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    
    val heights = (0 until barCount).map { index ->
        val duration = when(index) {
            0 -> 600
            1 -> 800
            2 -> 500
            3 -> 700
            else -> 600
        }
        val delay = index * 150
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightVal.value)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun QuickPickPortraitCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeAlpha = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "pulse")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "badgePulse"
        )
        animatedAlpha
    } else {
        1f
    }

    ElevatedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isPlaying) 8.dp else 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Full-bleed background image
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 2. Linear Gradient Scrim Overlay for contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
            
            // 3. Top-Right Pill Badge — only runs if this card is the active song
            if (isPlaying) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .graphicsLayer { alpha = badgeAlpha },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PLAYING",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            // 4. Bottom Typography & Quick Action Button
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isPlaying) Modifier.basicMarquee() else Modifier
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.width(6.dp))
                
                // Play / Pause circular button
                Surface(
                    onClick = onClick,
                    shape = CircleShape,
                    color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isPlaying) {
                            EqualizerAnimation(
                                modifier = Modifier
                                    .width(15.dp)
                                    .height(12.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                barCount = 3
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickPill(
    song: Song,
    width: Dp,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val targetBg = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 220),
        label = "QuickPickBg"
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(QuickPicksPillHeight),
        shape = RoundedCornerShape(QuickPicksPillHeight / 2),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artUri = song.albumArtUriString
            SmartImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                shape = CircleShape,
                modifier = Modifier.size(QuickPicksPillArtSize)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildQuickPickRows(songs: List<Song>): List<QuickPicksPillRow> {
    val groups = songs.chunked(QuickPicksPillsPerColumn).take(QuickPicksLimit / QuickPicksPillsPerColumn)
    val columns = groups.mapIndexed { colIndex, group ->
        val widthStep = QuickPicksWidthSteps[colIndex % QuickPicksWidthSteps.size]
        group.map { QuickPicksPillCell(it, widthStep) }
    }
    // Transpose columns -> rows
    val rows = mutableListOf<QuickPicksPillRow>()
    for (rowIdx in 0 until QuickPicksPillsPerColumn) {
        val pills = columns.mapNotNull { col -> col.getOrNull(rowIdx) }
        if (pills.isEmpty()) continue
        val totalWidth = pills.sumOf { it.width.value.toDouble() }.dp +
                QuickPicksPillSpacing * (pills.size - 1)
        rows.add(QuickPicksPillRow(pills, totalWidth))
    }
    return rows
}

@Composable
private fun QuickPickClassicCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 300),
        label = "ClassicCardBg"
    )
    
    // BUGFIX (was: a rememberInfiniteTransition was created unconditionally
    // on every QuickPicks card, even when not playing). Each
    // rememberInfiniteTransition starts a clock-driven animation that
    // runs forever and wakes the UI thread at ~16ms intervals to advance
    // the value. With ~10 cards on the home screen that's 10 parallel
    // animators ticking even when no card is playing — pure waste. We
    // now only create the transition when the card is actually playing.
    val animatedBorderGlowAlpha = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderGlow"
        ).value
    } else {
        1f
    }
    val borderGlowAlpha = animatedBorderGlowAlpha

    // M3 Expressive card: press-scale spring + corner morph instead of a static Card.
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
            .height(112.dp)
            .padding(bottom = 6.dp),
        cornerRadius = 24.dp,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isPlaying) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderGlowAlpha))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerAnimation(
                            modifier = Modifier
                                .width(20.dp)
                                .height(16.dp),
                            color = Color.White,
                            barCount = 3
                        )
                    }
                }
            }
            
            // Metadata details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                // Badge indicating state
                if (isPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "NOW PLAYING",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isPlaying) Modifier.basicMarquee() else Modifier
                )
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Action Button
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isPlaying) {
                        EqualizerAnimation(
                            modifier = Modifier
                                .width(14.dp)
                                .height(11.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            barCount = 3
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = if (isPlaying) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
