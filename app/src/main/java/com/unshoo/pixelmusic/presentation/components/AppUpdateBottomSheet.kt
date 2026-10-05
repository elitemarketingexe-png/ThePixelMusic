package com.unshoo.pixelmusic.presentation.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.update.AppReleaseAsset
import com.unshoo.pixelmusic.data.model.update.AppReleaseInfo
import com.unshoo.pixelmusic.data.update.AppUpdateDownloadManager
import com.unshoo.pixelmusic.data.update.UpdateDownloadState
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateBottomSheet(
    release: AppReleaseInfo,
    targetAsset: AppReleaseAsset?,
    currentVersion: String,
    downloadManager: AppUpdateDownloadManager,
    onDismissRequest: () -> Unit,
    onIgnoreVersion: ((String) -> Unit)? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val downloadState by downloadManager.downloadState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = {
            if (downloadState is UpdateDownloadState.Downloading) {
                downloadManager.cancelDownload()
            }
            onDismissRequest()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Bar with Update Available Pill + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "UPDATE AVAILABLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Hero Update Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AbsoluteSmoothCornerShape(24.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(18.dp, 60),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val displayTitle = remember(release.name, release.tagName) {
                            val raw = release.name?.takeIf { it.isNotBlank() } ?: "PixelMusic ${release.tagName}"
                            val cleaned = raw.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
                            if (cleaned.length > 20 && cleaned.contains("v1.")) {
                                cleaned.substringBefore("Expressive").substringBefore("Update").trim()
                            } else {
                                cleaned
                            }
                        }
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Version Progression Badge
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "v$currentVersion",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = release.tagName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Specs Row: Architecture, Size, Channel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val archLabel = targetAsset?.architecture?.substringBefore("(")?.trim()?.ifEmpty { "ARM64" } ?: "Universal"
                val sizeFormatted = targetAsset?.sizeBytes?.takeIf { it > 0 }?.let { bytes ->
                    val mb = bytes / (1024.0 * 1024.0)
                    String.format(Locale.US, "%.1f MB", mb)
                } ?: "Direct APK"

                // Card 1: Architecture
                Surface(
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = archLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Target ABI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Card 2: Package Size
                Surface(
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sizeFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Package Size",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Card 3: Source / Verification
                Surface(
                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Official",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "GitHub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Changelog / What's New Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AbsoluteSmoothCornerShape(20.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.presentation_batch_g_changelog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${release.tagName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 180.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val parsedLines = remember(release.body) {
                            val cleaned = release.body
                                .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
                                .trim()
                            cleaned.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() && !it.startsWith("|") && !it.startsWith("---") && !isRedundantChangelogHeader(it) }
                                .toList()
                        }

                        if (parsedLines.isNotEmpty()) {
                            parsedLines.forEach { line ->
                                val isBullet = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("•")
                                if (isBullet) {
                                    val itemText = line.removePrefix("- ").removePrefix("* ").removePrefix("• ").removePrefix("•").trim()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(6.dp)
                                        ) {}
                                        Text(
                                            text = parseMarkdownBold(itemText, boldColor = MaterialTheme.colorScheme.onSurface),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    val isHeader = line.endsWith(":") ||
                                            line.startsWith("⚡") ||
                                            line.startsWith("✨") ||
                                            line.startsWith("🚀")
                                    Text(
                                        text = parseMarkdownBold(line, boldColor = MaterialTheme.colorScheme.onSurface),
                                        style = if (isHeader) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = if (isHeader) Modifier.padding(top = 4.dp) else Modifier
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Performance enhancements and bug fixes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // DYNAMIC DOWNLOAD & ACTION SECTION
            AnimatedContent(
                targetState = downloadState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "download_action_content"
            ) { state ->
                val openGitHub = {
                    val webUrl = release.htmlUrl.ifEmpty { "https://github.com/ianshulyadav/PixelMusicApp/releases" }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) {}
                }

                when (state) {
                    is UpdateDownloadState.Downloading -> {
                        val animatedProgress by animateFloatAsState(
                            targetValue = state.progress,
                            label = "download_progress"
                        )
                        val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                        val downloadedMb = String.format(Locale.US, "%.1f", state.bytesDownloaded / (1024.0 * 1024.0))
                        val totalMb = if (state.totalBytes > 0) {
                            String.format(Locale.US, "%.1f", state.totalBytes / (1024.0 * 1024.0))
                        } else "45.2"

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = AbsoluteSmoothCornerShape(20.dp, 60),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Downloading update...",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "$percent%",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = GoogleSansRounded,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "$downloadedMb MB of $totalMb MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TextButton(
                                                onClick = openGitHub,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.OpenInNew,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "GitHub",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            TextButton(
                                                onClick = { downloadManager.cancelDownload() },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "Cancel",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Download Complete • Package verified",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = openGitHub,
                                    modifier = Modifier.height(52.dp),
                                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "GitHub",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = { downloadManager.installApk(state.apkFile) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Install Update",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = openGitHub,
                                    modifier = Modifier.height(50.dp),
                                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "GitHub", maxLines = 1)
                                }

                                Button(
                                    onClick = {
                                        val downloadUrl = targetAsset?.downloadUrl ?: release.htmlUrl
                                        val fileName = targetAsset?.name ?: "PixelMusic-${release.tagName}.apk"
                                        downloadManager.startDownload(downloadUrl, fileName)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp),
                                    shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Retry Download",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    is UpdateDownloadState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = openGitHub,
                                modifier = Modifier.height(52.dp),
                                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = {
                                    val downloadUrl = targetAsset?.downloadUrl ?: release.htmlUrl
                                    val fileName = targetAsset?.name ?: "PixelMusic-${release.tagName}.apk"
                                    downloadManager.startDownload(downloadUrl, fileName)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download Update",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Bottom "Ignore this version"
            if (onIgnoreVersion != null && downloadState !is UpdateDownloadState.Downloading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            onIgnoreVersion(release.tagName)
                            onDismissRequest()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Ignore this version",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

private val EMOJI_PREFIX_REGEX = Regex("""^[✨🎉🚀*#\s]+""")
private val REDUNDANT_HEADER_REGEX = Regex("""(?i)^what'?s\s*new(?:\s+in\s+v?[\d.]+)?[:\s]*$""")
private val MARKDOWN_BOLD_REGEX = Regex("""(\*\*|__)(.+?)\1""")

private fun isRedundantChangelogHeader(line: String): Boolean {
    val clean = line.replace(EMOJI_PREFIX_REGEX, "").trim()
    return clean.matches(REDUNDANT_HEADER_REGEX)
}

private fun parseMarkdownBold(
    text: String,
    boldColor: Color? = null
): AnnotatedString {
    val matches = MARKDOWN_BOLD_REGEX.findAll(text).toList()
    if (matches.isEmpty()) {
        return AnnotatedString(text)
    }
    return buildAnnotatedString {
        var currentIndex = 0
        for (match in matches) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }
            val boldContent = match.groupValues[2]
            if (boldColor != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                    append(boldContent)
                }
            } else {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldContent)
                }
            }
            currentIndex = match.range.last + 1
        }
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
