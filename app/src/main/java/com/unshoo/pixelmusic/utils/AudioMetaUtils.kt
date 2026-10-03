package com.unshoo.pixelmusic.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.unshoo.pixelmusic.data.database.MusicDao
import java.io.File
import java.util.Locale

data class AudioMeta(
    val mimeType: String?,
    val bitrate: Int?,      // bits per second
    val sampleRate: Int?,   // Hz
    val bitDepth: Int? = null,
    val formatLabel: String? = null,
    val provider: String? = null,
)

object AudioMetaUtils {

    /**
     * Returns audio metadata for a given file path.
     * Tries MediaMetadataRetriever first, then falls back to MediaExtractor.
     */
    suspend fun getAudioMetadata(musicDao: MusicDao, id: Long, filePath: String, deepScan: Boolean): AudioMeta {
        val cached = musicDao.getAudioMetadataById(id)
        if (!deepScan && cached != null &&
            cached.mimeType != null &&
            cached.bitrate != null &&
            cached.sampleRate != null
        )
            return cached

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return AudioMeta(null, null, null)

        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var bitDepth: Int? = null

        // Try MediaMetadataRetriever via pool
        MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                }
            } catch (e: Exception) {
                Log.w("AudioMetaUtils", "Retriever failed for $filePath: ${e.message}")
            }
        }

        // Fallback with MediaExtractor
        try {
            MediaExtractor().apply {
                setDataSource(filePath)
                for (i in 0 until trackCount) {
                    val format: MediaFormat = getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME)
                    if (trackMime?.startsWith("audio/") == true) {
                        mimeType = mimeType ?: trackMime
                        sampleRate =
                            sampleRate ?: format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        bitrate = bitrate ?: if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else null
                        if (bitDepth == null && format.containsKey("bits-per-sample")) {
                            bitDepth = format.getInteger("bits-per-sample")
                        }
                        break
                    }
                }
                release()
            }
        } catch (e: Exception) {
            Log.w("AudioMetaUtils", "Extractor failed for $filePath: ${e.message}")
        }

        return AudioMeta(mimeType, bitrate, sampleRate, bitDepth)

    }

    fun mimeTypeToFormat(mimeType: String?): String {
        val raw = mimeType
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return "-"

        if (raw.isBlank()) return "-"

        // If codecs parameter is explicitly present, extract and inspect it first
        if (raw.contains("codecs=")) {
            val codecPart = raw.substringAfter("codecs=").trim('"', '\'', ' ', ';')
            when {
                codecPart.contains("opus") -> return "opus"
                codecPart.contains("flac") -> return "flac"
                codecPart.contains("alac") -> return "alac"
                codecPart.contains("mp4a") -> return "m4a"
                codecPart.contains("aac") -> return "aac"
                codecPart.contains("vorbis") -> return "ogg"
            }
        }

        if (raw.contains("opus")) return "opus"
        if (raw.contains("flac")) return "flac"
        if (raw.contains("alac")) return "alac"
        if (raw.contains("vorbis")) return "ogg"
        if (raw.contains("webm")) return "opus"
        if (raw.contains("mp4a")) return "m4a"
        if (raw.contains("aac")) return "aac"

        val normalized = raw.substringBefore(';').trim()

        return when {
            normalized == "audio/mpeg" ||
                normalized == "audio/mp3" ||
                normalized == "audio/x-mp3" ||
                normalized == "audio/mpeg3" -> "mp3"

            normalized == "audio/flac" ||
                normalized == "audio/x-flac" -> "flac"

            normalized == "audio/wav" ||
                normalized == "audio/x-wav" ||
                normalized == "audio/wave" ||
                normalized == "audio/vnd.wave" -> "wav"

            normalized == "audio/ogg" ||
                normalized == "application/ogg" ||
                normalized == "audio/vorbis" ||
                normalized == "audio/x-vorbis" -> "ogg"

            normalized == "audio/opus" ||
                normalized == "audio/x-opus" -> "opus"

            normalized == "audio/webm" ||
                normalized == "video/webm" -> "opus"

            normalized == "audio/mp4" ||
                normalized == "audio/m4a" ||
                normalized == "audio/x-m4a" ||
                normalized == "audio/mp4a-latm" -> "m4a"

            normalized == "audio/aac" ||
                normalized == "audio/aacp" -> "aac"

            normalized == "audio/amr" ||
                normalized == "audio/amr-wb" ||
                normalized == "audio/3gpp" -> "amr"

            normalized == "audio/evrc" ||
                normalized == "audio/x-evrc" -> "evrc"

            normalized == "audio/qcelp" ||
                normalized == "audio/x-qcelp" -> "qcelp"

            normalized == "audio/x-ima-adpcm" ||
                normalized == "audio/ima-adpcm" -> "ima"

            normalized == "audio/alac" ||
                normalized == "audio/x-alac" -> "alac"

            normalized == "audio/aiff" ||
                normalized == "audio/x-aiff" ||
                normalized == "audio/aif" ||
                normalized == "audio/x-aifc" -> "aiff"

            normalized == "audio/x-ms-wma" ||
                normalized == "audio/wma" -> "wma"

            normalized == "audio/ac3" ||
                normalized == "audio/eac3" ||
                normalized == "audio/eac3-joc" -> "ac3"

            normalized == "audio/vnd.dts" ||
                normalized == "audio/vnd.dts.hd" -> "dts"

            normalized == "audio/midi" ||
                normalized == "audio/x-midi" ||
                normalized == "audio/sp-midi" ||
                normalized == "audio/x-mid" -> "midi"

            normalized.contains("mp4a") -> "m4a"
            normalized.contains("flac") -> "flac"
            normalized.contains("opus") -> "opus"
            normalized.contains("webm") -> "opus"
            normalized.contains("vorbis") || normalized.contains("ogg") -> "ogg"
            normalized.contains("wav") || normalized.contains("wave") -> "wav"
            normalized.contains("aac") -> "aac"
            normalized.contains("mpeg") || normalized.contains("mp3") -> "mp3"
            normalized.contains("amr") -> "amr"
            normalized.contains("alac") -> "alac"
            normalized.contains("aiff") || normalized.contains("aif") -> "aiff"
            normalized.contains("wma") -> "wma"
            normalized.contains("dts") -> "dts"
            normalized.contains("eac3") || normalized.contains("ac3") -> "ac3"
            normalized.contains("midi") || normalized.contains("x-mid") -> "midi"
            normalized.startsWith("audio/") -> normalized.substringAfter("audio/").ifBlank { "-" }
            else -> "-"
        }
    }

    /**
     * Formats the unified audio metadata label displayed in the Full Player file info tag
     * and the Share Card format badge.
     *
     * Prioritizes Hi-Res Lossless and Lossless tags over raw technical sample rates/bitrates.
     */
    fun formatAudioMetaLabel(
        mimeType: String?,
        bitrate: Int?,
        sampleRate: Int?,
        bitDepth: Int? = null,
        formatTag: String? = null,
        filePath: String? = null,
    ): String? {
        val pathLower = filePath?.lowercase(Locale.ROOT).orEmpty()
        // A codec that is lossy beyond doubt can never be Hi-Res / Lossless, whatever tag or
        // sample rate a caller (or stale state) hands us. See PlaybackStreamReconciler.
        val isDefinitelyLossy = AudioFamily.of(mimeType).isDefinitelyLossy
        val isFlacOrLossless = mimeType?.contains("flac", true) == true ||
                mimeType?.contains("alac", true) == true ||
                mimeType?.contains("wav", true) == true ||
                pathLower.endsWith(".flac") ||
                pathLower.endsWith(".alac") ||
                pathLower.endsWith(".wav") ||
                pathLower.endsWith(".aiff")

        val isHiRes = !isDefinitelyLossy &&
            (formatTag == "HI-RES LOSSLESS" || (sampleRate ?: 0) > 48000 || (bitDepth ?: 0) >= 24)

        if (isHiRes) {
            val codec = mimeTypeToFormat(mimeType)
                .takeIf { it != "-" }
                ?.uppercase(Locale.getDefault())
            return if (codec != null && codec != "FLAC") "HI-RES LOSSLESS • $codec" else "HI-RES LOSSLESS"
        }

        if (!isDefinitelyLossy && (formatTag == "LOSSLESS" || isFlacOrLossless)) {
            val codec = mimeTypeToFormat(mimeType)
                .takeIf { it != "-" }
                ?.uppercase(Locale.getDefault())
                ?: when {
                    pathLower.endsWith(".flac") -> "FLAC"
                    pathLower.endsWith(".alac") -> "ALAC"
                    pathLower.endsWith(".wav") -> "WAV"
                    pathLower.endsWith(".aiff") -> "AIFF"
                    else -> "FLAC"
                }
            return "LOSSLESS • $codec"
        }

        val formatLabel = mimeTypeToFormat(mimeType)
            .takeIf { it != "-" }
            ?.uppercase(Locale.getDefault())

        val parts = buildList {
            sampleRate?.takeIf { it > 0 }?.let { rate ->
                if (rate % 1000 == 0) {
                    add("${rate / 1000} kHz")
                } else {
                    add(String.format(Locale.US, "%.1f kHz", rate / 1000.0))
                }
            }
            bitrate?.takeIf { it > 0 }?.let { bitrateValue ->
                val kbpsLabel = "${bitrateValue / 1000} kbps"
                if (formatLabel != null) {
                    add("$kbpsLabel • $formatLabel")
                } else {
                    add(kbpsLabel)
                }
            } ?: formatLabel?.let { add(it) }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }

    /**
     * Formats the clean format tag badge displayed specifically on Story Share Cards.
     *
     * Unlike the detailed Full Player info label which includes sample rate and bitrate
     * ("48 kHz • 160 kbps • OPUS"), the Story Share Card badge displays a concise format tag
     * ("OPUS", "LOSSLESS", "HI-RES LOSSLESS", "FLAC", "MP3", "AAC").
     */
    fun formatShareCardAudioTag(
        mimeType: String?,
        formatTag: String? = null,
        filePath: String? = null,
        sampleRate: Int? = null,
        bitDepth: Int? = null,
    ): String {
        val isDefinitelyLossy = AudioFamily.of(mimeType).isDefinitelyLossy
        // Drop lossless tags that contradict a lossy codec instead of echoing them onto the card.
        val tag = formatTag?.takeUnless {
            isDefinitelyLossy && (it == "HI-RES LOSSLESS" || it == "LOSSLESS")
        }
        if (!isDefinitelyLossy &&
            (tag == "HI-RES LOSSLESS" || (sampleRate ?: 0) > 48000 || (bitDepth ?: 0) >= 24)
        ) {
            return "HI-RES LOSSLESS"
        }
        if (tag == "LOSSLESS") {
            return "LOSSLESS"
        }
        if (!tag.isNullOrBlank() && !tag.equals("null", true) && tag != "-") {
            val upper = tag.uppercase(Locale.getDefault())
            return if (upper == "WEBM") "OPUS" else upper
        }

        val format = mimeTypeToFormat(mimeType).lowercase(Locale.ROOT)
        val ext = filePath?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return when (if (format != "-") format else ext) {
            "flac", "alac", "wav", "aiff" -> "LOSSLESS"
            "opus", "webm" -> "OPUS"
            "mp3" -> "MP3"
            "aac", "m4a" -> "AAC"
            "ogg" -> "OGG"
            else -> if (format != "-") format.uppercase(Locale.getDefault()) else "OPUS"
        }
    }
}
