package com.unshoo.pixelmusic.presentation.components

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.service.player.HiFiCapabilityChecker
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import com.unshoo.pixelmusic.utils.AudioMetaUtils
import java.util.Locale

@Composable
fun AudioFormatInfoDialog(
    song: Song?,
    mimeType: String?,
    bitrate: Int?,
    sampleRate: Int?,
    bitDepth: Int? = null,
    formatTag: String? = null,
    provider: String? = null,
    filePath: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val resolvedFormatTag = remember(mimeType, bitrate, sampleRate, bitDepth, formatTag, filePath) {
        formatTag ?: AudioMetaUtils.formatAudioMetaLabel(
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            formatTag = formatTag,
            filePath = filePath
        )
    }

    val codecName = remember(mimeType, filePath) {
        val pathLower = filePath?.lowercase(Locale.ROOT).orEmpty()
        when {
            pathLower.endsWith(".flac") -> "FLAC"
            pathLower.endsWith(".alac") -> "ALAC"
            pathLower.endsWith(".wav") -> "WAV"
            pathLower.endsWith(".aiff") || pathLower.endsWith(".aif") -> "AIFF"
            pathLower.endsWith(".opus") -> "Opus"
            pathLower.endsWith(".m4a") -> "AAC / M4A"
            pathLower.endsWith(".mp3") -> "MP3"
            pathLower.endsWith(".ogg") -> "Ogg Vorbis"
            mimeType != null -> {
                val clean = AudioMetaUtils.mimeTypeToFormat(mimeType)
                if (clean != "-") clean.uppercase(Locale.ROOT) else mimeType.substringAfter("audio/").uppercase(Locale.ROOT)
            }
            else -> "Unknown"
        }
    }

    val sampleRateText = remember(sampleRate) {
        when {
            sampleRate == null || sampleRate <= 0 -> "44.1 kHz (Standard)"
            sampleRate >= 1000 -> String.format(Locale.US, "%.1f kHz", sampleRate / 1000.0)
            else -> "$sampleRate Hz"
        }
    }

    val bitDepthText = remember(bitDepth, mimeType, filePath) {
        val pathLower = filePath?.lowercase(Locale.ROOT).orEmpty()
        val isLossless = mimeType?.contains("flac", true) == true ||
                mimeType?.contains("alac", true) == true ||
                mimeType?.contains("wav", true) == true ||
                pathLower.endsWith(".flac") ||
                pathLower.endsWith(".alac") ||
                pathLower.endsWith(".wav")

        when {
            bitDepth != null && bitDepth > 0 -> "$bitDepth-bit"
            isLossless && (sampleRate ?: 0) > 48000 -> "24-bit (Hi-Res)"
            isLossless -> "16-bit (CD Quality)"
            else -> "16-bit (Standard)"
        }
    }

    val bitrateText = remember(bitrate) {
        when {
            bitrate == null || bitrate <= 0 -> null
            bitrate >= 1000 -> "${bitrate / 1000} kbps"
            else -> "$bitrate bps"
        }
    }

    val activeAudioDevice = remember(context) {
        getActiveAudioOutputDevice(context)
    }

    val isFloatSupported = remember {
        HiFiCapabilityChecker.isSupported()
    }

    val resolvedProvider = remember(provider, song, filePath) {
        when {
            !provider.isNullOrBlank() -> provider
            song?.isLocal == true || filePath != null -> "Local Storage"
            song?.youtubeId != null || song?.id?.startsWith("youtube_") == true -> "YouTube Music"
            else -> "Online Stream"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            FilledTonalButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text(
                    text = "Close",
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Audio Format Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!resolvedFormatTag.isNullOrBlank()) {
                            Text(
                                text = resolvedFormatTag,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Song summary header card
                if (song != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.displayArtist,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Grid of Specs
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "AUDIO SPECIFICATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 0.8.sp
                        )

                        FormatSpecRow(
                            icon = Icons.Rounded.AudioFile,
                            label = "Codec / Format",
                            value = codecName
                        )

                        FormatSpecRow(
                            icon = Icons.Rounded.GraphicEq,
                            label = "Resolution",
                            value = "$bitDepthText • $sampleRateText"
                        )

                        if (bitrateText != null) {
                            FormatSpecRow(
                                icon = Icons.Rounded.Speed,
                                label = "Bitrate",
                                value = bitrateText
                            )
                        }

                        FormatSpecRow(
                            icon = Icons.Rounded.CheckCircle,
                            label = "Channels",
                            value = "Stereo (2 Channels)"
                        )
                    }
                }

                // Output Hardware & Routing
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "OUTPUT & ROUTING",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 0.8.sp
                        )

                        FormatSpecRow(
                            icon = Icons.Rounded.Headphones,
                            label = "Active Device",
                            value = activeAudioDevice
                        )

                        FormatSpecRow(
                            icon = Icons.Rounded.Info,
                            label = "Hi-Fi PCM Float",
                            value = if (isFloatSupported) "Supported on this device" else "Unsupported (16-bit output)"
                        )
                    }
                }

                // Source & Location
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "SOURCE & STORAGE",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 0.8.sp
                        )

                        FormatSpecRow(
                            icon = Icons.Rounded.Info,
                            label = "Provider",
                            value = resolvedProvider
                        )

                        val displayPath = filePath ?: song?.contentUriString
                        if (!displayPath.isNullOrBlank() && !displayPath.startsWith("http")) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(displayPath))
                                        Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Location",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = displayPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy path",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
}

@Composable
private fun FormatSpecRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getActiveAudioOutputDevice(context: Context): String {
    val audioManager = context.getSystemService(AudioManager::class.java) ?: return "Default Output"
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val bestDevice = devices.maxByOrNull { device ->
        when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> 4
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> 3
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 1
            else -> 0
        }
    } ?: return "Default Audio Output"

    val typeLabel = when (bestDevice.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB DAC / Audio"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
        else -> "Audio Output"
    }

    val name = bestDevice.productName?.toString()?.takeIf { it.isNotBlank() }
    return if (name != null && name != typeLabel && !name.contains("speaker", ignoreCase = true)) {
        "$typeLabel ($name)"
    } else {
        typeLabel
    }
}
