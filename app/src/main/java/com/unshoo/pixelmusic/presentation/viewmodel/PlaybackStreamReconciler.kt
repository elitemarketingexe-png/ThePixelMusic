package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.utils.AudioFamily

data class PlaybackAudioMetadata(
    val mediaId: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null,
    val formatTag: String? = null,
    val sourceName: String? = null,
    /**
     * True only when the fields were derived from the format the player itself reports as
     * selected. False means a provisional guess (seeded at track change, before the player
     * has loaded anything) which the first confirmed format replaces.
     */
    val isConfirmed: Boolean = false,
) {
    val isLossless: Boolean
        get() = formatTag == "LOSSLESS" || formatTag == "HI-RES LOSSLESS" ||
            mimeType?.contains("flac", ignoreCase = true) == true ||
            mimeType?.contains("alac", ignoreCase = true) == true ||
            mimeType?.contains("wav", ignoreCase = true) == true

    val isHiRes: Boolean
        get() = formatTag == "HI-RES LOSSLESS" ||
            (sampleRate ?: 0) > 48000 ||
            (bitDepth ?: 0) >= 24
}

/** The audio format ExoPlayer reports as *selected* for the current item — ground truth. */
data class ConfirmedAudioFormat(
    val sampleMimeType: String?,
    val containerMimeType: String?,
    /** bits/s, null when the player does not know it (common for FLAC). */
    val bitrate: Int?,
    val sampleRate: Int?,
    val channelCount: Int?,
    val bitDepth: Int?,
) {
    val mimeType: String?
        get() = sampleMimeType?.takeIf { it.isNotBlank() } ?: containerMimeType
}

/**
 * What a resolver or cache says it *offered* for a track. An offer is not proof of playback:
 * the player may still be on the older stream while a better one is being resolved.
 */
data class OfferedStream(
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val sourceName: String?,
    val labelSaysHiRes: Boolean,
)

/**
 * Builds the one [PlaybackAudioMetadata] the UI shows, from the format the player actually
 * confirmed. Offers and earlier state may only *enrich* that format — they can never override
 * its codec, and can never grant a lossless tag to a lossy stream.
 *
 * Invariants (covered by tests):
 *  1. A lossy confirmed codec never carries a lossless / Hi-Res tag or a bit depth.
 *  2. A lossless tag is derived from the confirmed codec, never copied from earlier state.
 *  3. An offer is only used when its codec family equals the confirmed family.
 *  4. Unknown lossless fields stay null instead of being invented.
 */
object PlaybackStreamReconciler {

    private const val OPUS_MIME = "audio/webm; codecs=\"opus\""
    private const val AAC_MIME = "audio/mp4; codecs=\"mp4a.40.2\""

    // DASH chunk bitrates (30-65 kbps) are not the stream's real bitrate; ignore them.
    private const val BOGUS_LOW_MIN = 30_000
    private const val BOGUS_LOW_MAX = 65_000
    private const val MIN_TRUSTED_LOSSY_BITRATE = 65_000

    private const val DEFAULT_OPUS_BITRATE = 160_000
    private const val DEFAULT_LOSSY_BITRATE = 128_000
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val DEFAULT_LOSSY_SAMPLE_RATE = 44_100

    const val TAG_LOSSLESS = "LOSSLESS"
    const val TAG_HI_RES_LOSSLESS = "HI-RES LOSSLESS"

    fun reconcile(
        mediaId: String,
        confirmed: ConfirmedAudioFormat,
        offered: OfferedStream?,
        previous: PlaybackAudioMetadata?,
    ): PlaybackAudioMetadata {
        val confirmedFamily = AudioFamily.of(confirmed.mimeType)
        val offeredFamily = offered?.let { AudioFamily.of(it.mimeType) }

        val family = when {
            offered != null && offeredFamily?.isLossless == true && !confirmedFamily.isDefinitelyLossy -> offeredFamily
            confirmedFamily != AudioFamily.OTHER -> confirmedFamily
            offeredFamily != null && offeredFamily != AudioFamily.OTHER -> offeredFamily
            else -> confirmedFamily
        }
        val lossless = family.isLossless

        val offer = offered?.takeIf { AudioFamily.of(it.mimeType) == family }
        val prior = previous?.takeIf { AudioFamily.of(it.mimeType) == family }

        val confirmedBitrate = confirmed.bitrate?.takeIf { it > 0 }
        val confirmedSampleRate = confirmed.sampleRate?.takeIf { it > 0 }

        val mimeType = when (family) {
            AudioFamily.OPUS -> OPUS_MIME
            AudioFamily.AAC -> AAC_MIME
            else -> confirmed.mimeType ?: offer?.mimeType ?: prior?.mimeType
        }

        val bitrate = if (lossless) {
            confirmedBitrate
                ?: offer?.bitrate?.takeIf { it > 0 }
                ?: prior?.bitrate?.takeIf { it > 0 }
        } else {
            val isBogusLow = confirmedBitrate != null && confirmedBitrate in BOGUS_LOW_MIN..BOGUS_LOW_MAX
            val default = if (family == AudioFamily.OPUS) DEFAULT_OPUS_BITRATE else DEFAULT_LOSSY_BITRATE
            when {
                isBogusLow -> default
                confirmedBitrate != null && confirmedBitrate > MIN_TRUSTED_LOSSY_BITRATE -> confirmedBitrate
                else -> offer?.bitrate?.takeIf { it > MIN_TRUSTED_LOSSY_BITRATE }
                    ?: prior?.bitrate?.takeIf { it > MIN_TRUSTED_LOSSY_BITRATE }
                    ?: default
            }
        }

        val sampleRate = when {
            family == AudioFamily.OPUS -> OPUS_SAMPLE_RATE
            lossless -> confirmedSampleRate ?: offer?.sampleRate?.takeIf { it > 0 } ?: prior?.sampleRate
            else -> confirmedSampleRate
                ?: offer?.sampleRate?.takeIf { it > 0 }
                ?: prior?.sampleRate
                ?: DEFAULT_LOSSY_SAMPLE_RATE
        }

        // Bit depth only means something for lossless/PCM. Keeping a stale 24 on a lossy stream
        // is what made the label formatter print "HI-RES LOSSLESS • OPUS".
        val bitDepth = if (lossless) {
            confirmed.bitDepth ?: offer?.bitDepth ?: prior?.bitDepth
        } else {
            null
        }

        val formatTag = when {
            !lossless -> null
            (sampleRate ?: 0) > 48_000 ||
                (bitDepth ?: 0) >= 24 ||
                offer?.labelSaysHiRes == true -> TAG_HI_RES_LOSSLESS
            else -> TAG_LOSSLESS
        }

        val sourceName = offer?.sourceName
            ?: prior?.sourceName
            ?: previous?.sourceName?.takeIf { it in NON_RESOLVER_SOURCES }
            ?: "YouTube"

        return PlaybackAudioMetadata(
            mediaId = mediaId,
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channelCount = confirmed.channelCount?.takeIf { it > 0 } ?: prior?.channelCount,
            bitDepth = bitDepth,
            formatTag = formatTag,
            sourceName = sourceName,
            isConfirmed = true,
        )
    }

    /** Source names that are not lossless resolver providers, so they cannot go stale on a codec change. */
    private val NON_RESOLVER_SOURCES = setOf("Local Storage", "JioSaavn", "YouTube")
}
