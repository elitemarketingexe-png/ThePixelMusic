package com.unshoo.pixelmusic.data.remote.saavn

import com.unshoo.pixelmusic.data.preferences.StreamingAudioQuality

/**
 * Ported from vivi-music's SaavnAudioQuality.
 * Represents audio streaming quality levels available on JioSaavn (AAC-LC inside MP4 container).
 *
 * Provides:
 * - [toApiValue]: string sent to the JioSaavn API ("320kbps", "160kbps", "96kbps").
 * - [toLabel]: human-readable label for Settings UI ("High (320 kbps)", etc.).
 * - [resolveQuality]: wires JioSaavn quality with the app's existing [StreamingAudioQuality]
 *   when set to [AUTO].
 */
enum class SaavnAudioQuality(val apiValue: String, val bitrateKbps: Int, val label: String) {
    AUTO("320kbps", 320, "Auto (Follow streaming quality)"),
    QUALITY_320("320kbps", 320, "High (320 kbps)"),
    QUALITY_160("160kbps", 160, "Medium (160 kbps)"),
    QUALITY_96("96kbps", 96, "Low (96 kbps)");

    fun toApiValue(): String = apiValue
    fun toLabel(): String = label

    companion object {
        fun fromName(name: String?): SaavnAudioQuality {
            return entries.find { it.name == name } ?: AUTO
        }

        /**
         * Resolves the effective [SaavnAudioQuality] based on user preference and current network
         * streaming quality plan.
         */
        fun resolveQuality(
            userSetting: SaavnAudioQuality,
            streamingQuality: StreamingAudioQuality,
            maxBitrateKbps: Int = 0
        ): SaavnAudioQuality {
            if (userSetting != AUTO) return userSetting
            return when {
                streamingQuality == StreamingAudioQuality.LOW || (maxBitrateKbps in 1..127) -> QUALITY_96
                streamingQuality == StreamingAudioQuality.MEDIUM || (maxBitrateKbps in 128..255) -> QUALITY_160
                else -> QUALITY_320
            }
        }
    }
}
