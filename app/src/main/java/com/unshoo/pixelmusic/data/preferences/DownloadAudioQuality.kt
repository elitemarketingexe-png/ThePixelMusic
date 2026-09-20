package com.unshoo.pixelmusic.data.preferences

/**
 * Download audio quality levels.
 *
 * - MAX: Lossless (FLAC/ALAC) via lossless sources – only available when lossless API is enabled.
 * - HIGH: JioSaavn 320 kbps AAC.
 * - MEDIUM: JioSaavn 160 kbps AAC.
 * - LOW: Direct YouTube download (Opus/WebM).
 */
enum class DownloadAudioQuality(val label: String) {
    MAX("Max (Lossless)"),
    HIGH("High (320 kbps)"),
    MEDIUM("Medium (160 kbps)"),
    LOW("Low (YouTube)");

    companion object {
        fun fromName(name: String?): DownloadAudioQuality =
            entries.find { it.name == name } ?: HIGH
    }
}
