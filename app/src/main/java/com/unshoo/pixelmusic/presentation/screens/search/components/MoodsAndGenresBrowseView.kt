package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight

/**
 * Musify-style pure YouTube Music Moods & Moments and Genres browse grid.
 * Strictly uses authentic YouTube Music categories (no local genres).
 * Renders each item as a borderless dual-image Musify card with spring physics.
 */
@Composable
fun MoodsAndGenresBrowseView(
    searchHistory: List<SearchHistoryItem>,
    bottomBarHeight: Dp,
    topPadding: Dp = 8.dp,
    listState: LazyListState = rememberLazyListState(),
    onMoodGenreClick: (MoodGenreItem) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var liveMoods by remember { mutableStateOf(MoodGenreCatalog.cachedMoods) }
    var liveGenres by remember { mutableStateOf(MoodGenreCatalog.cachedGenres) }

    LaunchedEffect(Unit) {
        CategoryPlaylistImageCache.init(context)
        if (!MoodGenreCatalog.isLiveLoaded) {
            val liveData = MoodGenreCatalog.loadLiveMoodsAndGenres()
            if (liveData != null) {
                liveMoods = liveData.first
                liveGenres = liveData.second
            }
        } else {
            launch(Dispatchers.IO) {
                MoodGenreCatalog.prefetchFeaturedPlaylistCovers(liveMoods + liveGenres)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = bottomBarHeight + MiniPlayerHeight + 32.dp
        )
    ) {
        // Recent Searches Section (if history exists)
        if (searchHistory.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Searches",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    TextButton(onClick = onClearAllHistory) {
                        Text(
                            text = "Clear all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item(key = "history_chips") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(
                        items = searchHistory.take(8),
                        key = { "hist_chip_${it.id ?: it.query}" }
                    ) { item ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .bounceClick(scaleDown = 0.94f) { onHistoryItemClick(item.query) }
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.query,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { onHistoryItemDelete(item.query) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Moods & moments (Matching Image 3)
        val moodsTopPadding = if (searchHistory.isEmpty()) 14.dp else 8.dp
        item(key = "moods_header") {
            Text(
                text = "Moods & moments",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = moodsTopPadding, bottom = 12.dp)
            )
        }

        items(
            items = liveMoods.chunked(2),
            key = { "mood_row_${it.first().id}" }
        ) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MoodGenreCard(
                    item = pair[0],
                    onClick = { onMoodGenreClick(pair[0]) },
                    modifier = Modifier.weight(1f)
                )
                if (pair.size > 1) {
                    MoodGenreCard(
                        item = pair[1],
                        onClick = { onMoodGenreClick(pair[1]) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Section: Genres (Matching Image 2)
        item(key = "genres_header") {
            Text(
                text = "Genres",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 14.dp)
            )
        }

        items(
            items = liveGenres.chunked(2),
            key = { "genre_row_${it.first().id}" }
        ) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MoodGenreCard(
                    item = pair[0],
                    onClick = { onMoodGenreClick(pair[0]) },
                    modifier = Modifier.weight(1f)
                )
                if (pair.size > 1) {
                    MoodGenreCard(
                        item = pair[1],
                        onClick = { onMoodGenreClick(pair[1]) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
