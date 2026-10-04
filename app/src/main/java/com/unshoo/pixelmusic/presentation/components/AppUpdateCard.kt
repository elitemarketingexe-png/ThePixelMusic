package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.data.model.update.UpdateCheckResult
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppUpdateCard(
    currentVersion: String,
    isChecking: Boolean,
    updateResult: UpdateCheckResult?,
    autoCheckEnabled: Boolean,
    lastCheckTime: Long,
    onCheckForUpdates: () -> Unit,
    onOpenUpdateSheet: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cardShape = AbsoluteSmoothCornerShape(24.dp, 60)

    val lastCheckedText = remember(lastCheckTime) {
        if (lastCheckTime > 0) {
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            "Checked ${format.format(Date(lastCheckTime))}"
        } else {
            "Not checked yet"
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Main clickable row: status + button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (updateResult is UpdateCheckResult.Available) {
                            onOpenUpdateSheet()
                        } else {
                            onCheckForUpdates()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                val iconContainerColor = when {
                    updateResult is UpdateCheckResult.Available -> MaterialTheme.colorScheme.primaryContainer
                    updateResult is UpdateCheckResult.UpToDate -> MaterialTheme.colorScheme.tertiaryContainer
                    updateResult is UpdateCheckResult.Error -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                }

                val iconContentColor = when {
                    updateResult is UpdateCheckResult.Available -> MaterialTheme.colorScheme.onPrimaryContainer
                    updateResult is UpdateCheckResult.UpToDate -> MaterialTheme.colorScheme.onTertiaryContainer
                    updateResult is UpdateCheckResult.Error -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }

                Surface(
                    shape = CircleShape,
                    color = iconContainerColor,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = Triple(isChecking, updateResult, Unit),
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "update_icon"
                        ) { (checking, result, _) ->
                            when {
                                checking -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = iconContentColor
                                    )
                                }
                                result is UpdateCheckResult.Available -> {
                                    Icon(
                                        imageVector = Icons.Rounded.SystemUpdate,
                                        contentDescription = null,
                                        tint = iconContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                result is UpdateCheckResult.UpToDate -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = iconContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                result is UpdateCheckResult.Error -> {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = iconContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = iconContentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Text Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (updateResult) {
                            is UpdateCheckResult.Available -> "Update Available"
                            is UpdateCheckResult.UpToDate -> "Up to Date"
                            is UpdateCheckResult.Error -> "Check Failed"
                            else -> "Check for Updates"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = when (updateResult) {
                            is UpdateCheckResult.Available -> "${updateResult.release.tagName} ready • Tap to install"
                            is UpdateCheckResult.UpToDate -> "v$currentVersion • $lastCheckedText"
                            is UpdateCheckResult.Error -> updateResult.message
                            else -> "v$currentVersion • $lastCheckedText"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (updateResult is UpdateCheckResult.Available) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Check Action Chip / Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (updateResult is UpdateCheckResult.Available) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (updateResult is UpdateCheckResult.Available) {
                                onOpenUpdateSheet()
                            } else {
                                onCheckForUpdates()
                            }
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = if (updateResult is UpdateCheckResult.Available) "Update" else "Check",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (updateResult is UpdateCheckResult.Available) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            // Auto-check in background toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatic Background Check",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Notify when a new release is published to GitHub",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleAutoCheck(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
