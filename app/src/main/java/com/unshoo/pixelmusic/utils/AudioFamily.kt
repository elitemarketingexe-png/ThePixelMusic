package com.unshoo.pixelmusic.utils

import java.util.Locale

/**
 * Codec family of an audio stream, derived from a mime string.
 *
 * Two jobs: (1) compare what the player is decoding against what a resolver *offered*
 * ("is this the same kind of stream?"), and (2) give the label formatter a hard guarantee
 * that a lossy codec can never be presented as lossless / Hi-Res.
 *
 * Pure Kotlin on purpose (no Android types) so it is trivially unit-testable.
 */
enum class AudioFamily(val isLossless: Boolean) {
    OPUS(false),
    AAC(false),
    VORBIS(false),
    MP3(false),
    FLAC(true),
    ALAC(true),
    PCM(true),
    OTHER(false);

    /** Codecs that are lossy beyond any doubt. AAC is excluded: `audio/mp4` may carry ALAC. */
    val isDefinitelyLossy: Boolean
        get() = this == OPUS || this == VORBIS || this == MP3

    companion object {
        /**
         * Order matters: an explicit codec token wins over its container
         * (`audio/webm; codecs="vorbis"` is Vorbis, not Opus; `audio/mp4; codecs="alac"` is ALAC).
         */
        fun of(mimeType: String?): AudioFamily {
            val raw = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (raw.isEmpty()) return OTHER
            return when {
                raw.contains("opus") -> OPUS
                raw.contains("flac") -> FLAC
                raw.contains("alac") -> ALAC
                raw.contains("vorbis") -> VORBIS
                raw.contains("mp4a") || raw.contains("aac") -> AAC
                raw.contains("mpeg") || raw.contains("mp3") -> MP3
                raw.contains("wav") || raw.contains("wave") ||
                    raw.contains("raw") || raw.contains("pcm") || raw.contains("aiff") -> PCM
                raw.contains("webm") -> OPUS // bare webm audio on YouTube is Opus
                raw.contains("mp4") -> AAC
                else -> OTHER
            }
        }
    }
}
