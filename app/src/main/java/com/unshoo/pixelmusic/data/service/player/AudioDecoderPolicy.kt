package com.unshoo.pixelmusic.data.service.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import java.util.Locale

@UnstableApi
internal object AudioDecoderPolicy {
    private const val AUDIO_MIDI = "audio/midi"
    private val extensionOnlyMimeTypes = setOf(
        MimeTypes.AUDIO_ALAC,
        MimeTypes.AUDIO_EXOPLAYER_MIDI,
        AUDIO_MIDI
    )

    fun shouldUseExtensionRenderer(mimeType: String, usesFloatOutput: Boolean = false): Boolean {
        // Some platform FLAC decoders produce corrupt audio or seek lockups when Media3 requests PCM_FLOAT
        // (reported on the Galaxy S25 Ultra, issue #122). FFmpeg can decode FLAC directly to
        // float PCM without that platform negotiation. Keep the normal decoder order in the
        // integer output modes.
        return (usesFloatOutput && MimeTypes.AUDIO_FLAC.equals(mimeType, ignoreCase = true)) ||
            extensionOnlyMimeTypes.any { it.equals(mimeType, ignoreCase = true) }
    }

    fun <T> selectPlatformDecoders(
        mimeType: String,
        decoderInfos: List<T>,
        usesFloatOutput: Boolean = false
    ): List<T> = if (shouldUseExtensionRenderer(mimeType, usesFloatOutput)) emptyList() else decoderInfos

    fun isLikelyHardwareDecoder(decoderName: String): Boolean {
        val normalized = decoderName.lowercase(Locale.US)
        val knownSoftwareTokens = listOf(
            "omx.google.",
            "c2.android.",
            "ffmpeg",
            "midi",
            "jsyn",
            "libgav1",
            "dav1d"
        )
        if (knownSoftwareTokens.any(normalized::contains)) return false

        return normalized.startsWith("omx.") ||
            normalized.startsWith("c2.") ||
            normalized.contains(".qti.") ||
            normalized.contains(".qcom.") ||
            normalized.contains(".sec.") ||
            normalized.contains(".mtk.") ||
            normalized.contains(".exynos.") ||
            normalized.contains(".dolby.")
    }
}
