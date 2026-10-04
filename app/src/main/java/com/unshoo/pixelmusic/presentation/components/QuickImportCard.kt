package com.unshoo.pixelmusic.presentation.components

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

enum class QuickImportSource {
    SPOTIFY,
    YOUTUBE
}

@Composable
fun QuickImportCard(
    modifier: Modifier = Modifier,
    onImportSpotify: (url: String, onDone: (Result<String>) -> Unit) -> Unit,
    onImportYouTube: (url: String, onDone: (Result<String>) -> Unit) -> Unit,
    onSuccess: (playlistId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedSource by remember { mutableStateOf(QuickImportSource.SPOTIFY) }
    var urlText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val cardShape = AbsoluteSmoothCornerShape(26.dp, 60)
    val spotifyGreen = Color(0xFF1DB954)
    val youtubeRed = Color(0xFFFF0000)

    fun submit() {
        val trimmed = urlText.trim()
        if (trimmed.isBlank()) {
            Toast.makeText(context, "Please enter a valid playlist link", Toast.LENGTH_SHORT).show()
            return
        }

        keyboardController?.hide()
        isLoading = true

        val onDone: (Result<String>) -> Unit = { result ->
            isLoading = false
            if (result.isSuccess) {
                val pId = result.getOrThrow()
                Toast.makeText(context, "Playlist imported successfully! Syncing songs...", Toast.LENGTH_SHORT).show()
                urlText = ""
                onSuccess(pId)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to import playlist"
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }

        when (selectedSource) {
            QuickImportSource.SPOTIFY -> onImportSpotify(trimmed, onDone)
            QuickImportSource.YOUTUBE -> onImportYouTube(trimmed, onDone)
        }
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Cloud Icon + Title + Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_import_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.quick_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tab Buttons: Spotify vs YouTube
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Spotify Tab
                val isSpotify = selectedSource == QuickImportSource.SPOTIFY
                val spotifyBg by animateColorAsState(
                    targetValue = if (isSpotify) spotifyGreen else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "spotifyBg"
                )
                val spotifyContentColor = if (isSpotify) Color.White else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(spotifyBg)
                        .clickable {
                            selectedSource = QuickImportSource.SPOTIFY
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_spotify),
                        contentDescription = null,
                        tint = if (isSpotify) Color.White else spotifyGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Spotify",
                        fontWeight = if (isSpotify) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = GoogleSansRounded,
                        fontSize = 14.sp,
                        color = spotifyContentColor
                    )
                }

                // YouTube Tab
                val isYouTube = selectedSource == QuickImportSource.YOUTUBE
                val ytBg by animateColorAsState(
                    targetValue = if (isYouTube) youtubeRed else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ytBg"
                )
                val ytTextColor = if (isYouTube) Color.White else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ytBg)
                        .clickable {
                            selectedSource = QuickImportSource.YOUTUBE
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isYouTube) {
                        Icon(
                            painter = painterResource(R.drawable.ic_youtube_cutout),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_youtube),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "YouTube",
                        fontWeight = if (isYouTube) FontWeight.Bold else FontWeight.Medium,
                        fontFamily = GoogleSansRounded,
                        fontSize = 14.sp,
                        color = ytTextColor
                    )
                }
            }

            // Description text
            Text(
                text = if (selectedSource == QuickImportSource.SPOTIFY) {
                    "Import public playlist from Spotify"
                } else {
                    "Import public playlist from YT Music"
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // URL Input pill container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (urlText.isEmpty()) {
                            Text(
                                text = if (selectedSource == QuickImportSource.SPOTIFY) {
                                    "https://open.spotify.com/playlist/..."
                                } else {
                                    "https://music.youtube.com/playlist?list=..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        BasicTextField(
                            value = urlText,
                            onValueChange = { urlText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = GoogleSansRounded
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submit() }
                            )
                        )
                    }

                    if (urlText.isNotEmpty() && !isLoading) {
                        IconButton(
                            onClick = { urlText = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Circular Submit Action Button
                    IconButton(
                        onClick = { submit() },
                        enabled = !isLoading && urlText.isNotBlank(),
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Import",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
