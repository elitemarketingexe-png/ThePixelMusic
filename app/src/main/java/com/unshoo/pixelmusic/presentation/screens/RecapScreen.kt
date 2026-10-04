package com.unshoo.pixelmusic.presentation.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.ListeningPersonality
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.RecapPeriod
import com.unshoo.pixelmusic.presentation.viewmodel.RecapViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.RecapUiState
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.absoluteValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecapScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    recapViewModel: RecapViewModel = hiltViewModel(),
    exploreViewModel: ExploreViewModel = hiltViewModel()
) {
    val state by recapViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val view = LocalView.current

    // Extract dominant palette color for background tint using optimized non-blocking extractor
    val animatedColor = rememberDominantCardColor(
        imageUrl = state.dominantCoverUrl,
        baseColor = colors.surfaceContainer,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 1.0f,
        lightBlendFraction = 1.0f
    )

    val backgroundBrush = remember(animatedColor, colors.background) {
        Brush.radialGradient(
            colors = listOf(animatedColor.copy(alpha = 0.35f), colors.background),
            radius = 1200f
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundBrush)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Period Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RecapPeriod.values().forEach { period ->
                val isSelected = state.selectedPeriod == period
                FilterChip(
                    selected = isSelected,
                    onClick = { recapViewModel.selectPeriod(period) },
                    label = { Text(period.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.primary,
                        selectedLabelColor = colors.onPrimary,
                        containerColor = colors.surfaceContainerHigh.copy(alpha = 0.5f),
                        labelColor = colors.onSurface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = null
                )
            }
        }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.recap_failed_to_calculate), fontWeight = FontWeight.Bold)
                    Text(state.error ?: "Unknown error", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { recapViewModel.selectPeriod(state.selectedPeriod) }) {
                        Text(stringResource(R.string.library_retry))
                    }
                }
            }
        } else if (state.totalListeningMinutes < 10) {
            // Graceful Empty State
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = AbsoluteSmoothCornerShape(32.dp, 60),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.primary
                    )
                    Text(
                        text = stringResource(R.string.recap_is_building_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.recap_is_building_desc),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { exploreViewModel.setSelectedFilter("All") },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.recap_explore_music_now),
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded
                        )
                    }
                }
            }
        } else {
            // The 5-card HorizontalPager story layout
            val pagerState = rememberPagerState(pageCount = { 5 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(520.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 16.dp
            ) { page ->
                val pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = lerp(1f, 0.92f, pageOffset.coerceIn(0f, 1f))
                val alpha = lerp(1f, 0.6f, pageOffset.coerceIn(0f, 1f))

                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    shape = AbsoluteSmoothCornerShape(32.dp, 60),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer.copy(alpha = 0.85f))
                ) {
                    when (page) {
                        0 -> OverviewCard(state)
                        1 -> TopTracksCard(state, recapViewModel, playerViewModel)
                        2 -> TopArtistsCard(state, playerViewModel)
                        3 -> TimeOfDayCard(state)
                        4 -> ShareCard(state, recapViewModel, view)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(state: RecapUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.recap_listening_in_numbers),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.recap_summary_journeys),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.recap_listening_time), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.totalListeningMinutes} mins",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = GoogleSansRounded
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(colors.surfaceContainerHigh.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.recap_discovered_artists), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${state.totalUniqueArtists}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    fontFamily = GoogleSansRounded
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(state.listeningPersonality.emoji, fontSize = 42.sp)
                Column {
                    Text(
                        text = state.listeningPersonality.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        color = colors.onPrimaryContainer
                    )
                    Text(
                        text = state.listeningPersonality.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopTracksCard(
    state: RecapUiState,
    recapViewModel: RecapViewModel,
    playerViewModel: PlayerViewModel
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Tracks on Repeat",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.topSongs.take(5).forEachIndexed { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "#${index + 1}",
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            fontSize = 18.sp,
                            fontFamily = GoogleSansRounded
                        )
                        SmartImage(
                            model = song.albumArtUri,
                            contentDescription = song.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${song.playCount} plays",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { recapViewModel.playTopTracks(playerViewModel) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.recap_play_top_tracks_now), fontWeight = FontWeight.Bold, fontFamily = GoogleSansRounded)
        }
    }
}

@Composable
private fun TopArtistsCard(
    state: RecapUiState,
    playerViewModel: PlayerViewModel
) {
    val colors = MaterialTheme.colorScheme
    val topArtistName = state.topArtists.firstOrNull()?.artist ?: "Unknown Artist"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Your Most Played Artists",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.Bold,
            color = colors.onSurface
        )

        // Circle staggered avatar layout
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(
                items = state.topArtists.take(5),
                key = { _, artist -> artist.artist }
            ) { index, artist ->
                val size = if (index == 0) 90.dp else 64.dp
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.artist.take(1).uppercase(),
                            fontSize = if (index == 0) 36.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artist.artist,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🏆 Hero Artist Highlight",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val hours = (state.topArtists.firstOrNull()?.totalDurationMs ?: 0L) / 1000 / 60 / 60
                val mins = ((state.topArtists.firstOrNull()?.totalDurationMs ?: 0L) / 1000 / 60) % 60
                Text(
                    text = "You spent $hours hrs $mins mins listening to $topArtistName!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Button(
            onClick = {
                playerViewModel.playRadio(
                    endpoint = unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                        playlistId = "RDAMVM$topArtistName",
                        videoId = null
                    ),
                    title = "$topArtistName Radio",
                    artistName = topArtistName
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer)
        ) {
            Icon(Icons.Rounded.Radio, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start #1 Artist Radio", fontWeight = FontWeight.Bold, fontFamily = GoogleSansRounded)
        }
    }
}

@Composable
private fun TimeOfDayCard(state: RecapUiState) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "When You Tune In",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface
            )
            Text(
                text = "Peak hour highlight: ${state.peakListeningTimeOfDay}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Static representation for chart heights since summary does not export strict buckets
            // Map 4 times of day based on peak time
            val morningVal = if (state.peakListeningTimeOfDay == "Morning") 0.9f else 0.4f
            val afternoonVal = if (state.peakListeningTimeOfDay == "Afternoon") 0.9f else 0.5f
            val eveningVal = if (state.peakListeningTimeOfDay == "Evening") 0.9f else 0.6f
            val nightVal = if (state.peakListeningTimeOfDay == "Night Owl") 0.9f else 0.3f

            TimeBar("Morning", morningVal, state.peakListeningTimeOfDay == "Morning")
            TimeBar("Afternoon", afternoonVal, state.peakListeningTimeOfDay == "Afternoon")
            TimeBar("Evening", eveningVal, state.peakListeningTimeOfDay == "Evening")
            TimeBar("Late Night", nightVal, state.peakListeningTimeOfDay == "Night Owl")
        }

        Text(
            text = "You're a prime-time ${state.peakListeningTimeOfDay.lowercase()} listener!",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
            fontWeight = FontWeight.SemiBold,
            color = colors.primary
        )
    }
}

@Composable
private fun RowScope.TimeBar(label: String, fraction: Float, isPeak: Boolean) {
    val colors = MaterialTheme.colorScheme
    val barHeight by animateFloatAsState(targetValue = fraction, animationSpec = tween(1000), label = "bar_height")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.weight(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(barHeight)
                .width(28.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                .background(if (isPeak) colors.primary else colors.outlineVariant)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, fontSize = 10.sp, maxLines = 1, fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ShareCard(state: RecapUiState, recapViewModel: RecapViewModel, rootView: android.view.View) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Styled share summary card container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ThePixelMusic", fontWeight = FontWeight.Bold, fontFamily = GoogleSansRounded, color = colors.primary)
                    Text(state.selectedPeriod.label, style = MaterialTheme.typography.bodySmall)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!state.dominantCoverUrl.isNullOrBlank()) {
                        SmartImage(
                            model = state.dominantCoverUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("My Music Recap", fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = GoogleSansRounded)
                        Text("${state.totalListeningMinutes} Minutes listened", style = MaterialTheme.typography.bodyMedium)
                        Text("Personality: ${state.listeningPersonality.emoji} ${state.listeningPersonality.title}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Text(
                    text = "Top Artist: ${state.topArtists.firstOrNull()?.artist ?: "None"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    recapViewModel.saveRecapAsPlaylist { success ->
                        if (success) {
                            Toast.makeText(context, "Saved recap playlist successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to create playlist.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.recap_save_playlist), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    val summary = "🎵 My ThePixelMusic Recap! 🎵\n\n" +
                            "Minutes listened: ${state.totalListeningMinutes} mins\n" +
                            "Personality: ${state.listeningPersonality.emoji} ${state.listeningPersonality.title}\n" +
                            "Top artist: ${state.topArtists.firstOrNull()?.artist ?: "None"}\n" +
                            "Top track: ${state.topSongs.firstOrNull()?.title ?: "None"} by ${state.topSongs.firstOrNull()?.artist ?: "None"}\n\n" +
                            "Shared via ThePixelMusic 🚀"
                    shareRecapBitmap(context, rootView, summary)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.recap_share_recap), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun shareRecapBitmap(context: android.content.Context, view: android.view.View, textSummary: String) {
    try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        view.draw(canvas)

        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val imagePath = java.io.File(cachePath, "recap_share.png")
        val stream = java.io.FileOutputStream(imagePath)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imagePath
        )

        if (contentUri != null) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, textSummary)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share your recap"))
        }
    } catch (e: Exception) {
        // Fallback to text share
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textSummary)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share your recap"))
    }
}
