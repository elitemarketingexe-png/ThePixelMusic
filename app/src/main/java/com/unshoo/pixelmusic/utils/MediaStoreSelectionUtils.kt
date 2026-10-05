package com.unshoo.pixelmusic.utils

import android.provider.MediaStore

private val SPECIAL_FORMAT_MIME_SELECTION_ARGS = arrayOf(
    "audio/midi",
    "audio/x-midi",
    "audio/sp-midi",
    "audio/x-mid",
    "audio/alac",
    "audio/x-alac",
    "audio/caf",
    "audio/x-caf",
    "audio/mp4",
    "audio/m4a",
    "audio/x-m4a",
    "audio/aac",
    "audio/mp4a-latm"
)
private val SPECIAL_FORMAT_EXTENSION_SELECTION_ARGS = arrayOf(
    "%.mid",
    "%.midi",
    "%.alac",
    "%.caf",
    "%.m4a",
    "%.m4b",
    "%.m4p",
    "%.aac"
)

/**
 * Builds the baseline MediaStore selection for user-facing local audio.
 *
 * We intentionally do not rely on [MediaStore.Audio.Media.IS_MUSIC] here because some devices
 * and scanners leave valid songs flagged as non-music, which makes library sync and folder
 * browsing appear to "cap out" below the real file count for specific users.
 *
 * MIDI, ALAC/CAF, and M4A/AAC files may be indexed with incomplete duration metadata by Android's MediaScanner,
 * so explicit MIME/path matches bypass the duration floor and are augmented during sync.
 */
fun buildLocalAudioSelection(minDurationMs: Int): Pair<String, Array<String>> {
    val clampedMinDurationMs = minDurationMs.coerceAtLeast(0)
    val mimePlaceholders = SPECIAL_FORMAT_MIME_SELECTION_ARGS.joinToString(",") { "?" }
    val extensionSelection = SPECIAL_FORMAT_EXTENSION_SELECTION_ARGS.joinToString(" OR ") {
        "LOWER(${MediaStore.Audio.Media.DATA}) LIKE ?"
    }
    val selection = buildString {
        append("(")
        append("${MediaStore.Audio.Media.DURATION} >= ?")
        append(" OR LOWER(COALESCE(${MediaStore.Audio.Media.MIME_TYPE}, '')) IN ($mimePlaceholders)")
        append(" OR $extensionSelection")
        append(")")
        append(" AND (COALESCE(${MediaStore.Audio.Media.TITLE}, '') != '' OR ${MediaStore.Audio.Media.DATA} IS NOT NULL)")
        append(" AND ${MediaStore.Audio.Media.DATA} IS NOT NULL")
    }
    return selection to arrayOf(clampedMinDurationMs.toString()) +
        SPECIAL_FORMAT_MIME_SELECTION_ARGS +
        SPECIAL_FORMAT_EXTENSION_SELECTION_ARGS
}

