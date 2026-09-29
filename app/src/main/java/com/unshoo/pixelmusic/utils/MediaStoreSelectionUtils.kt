package com.unshoo.pixelmusic.utils

import android.provider.MediaStore

private val INCOMPLETE_DURATION_MIME_SELECTION_ARGS = arrayOf(
    "audio/midi",
    "audio/x-midi",
    "audio/sp-midi",
    "audio/x-mid",
    "audio/alac",
    "audio/x-alac",
    "audio/caf",
    "audio/x-caf"
)
private val INCOMPLETE_DURATION_EXTENSION_SELECTION_ARGS = arrayOf(
    "%.mid",
    "%.midi",
    "%.alac",
    "%.caf"
)

/**
 * Builds the baseline MediaStore selection for user-facing local audio.
 *
 * We intentionally do not rely on [MediaStore.Audio.Media.IS_MUSIC] here because some devices
 * and scanners leave valid songs flagged as non-music, which makes library sync and folder
 * browsing appear to "cap out" below the real file count for specific users.
 *
 * MIDI and ALAC/lossless files may be indexed with incomplete duration metadata, so explicit
 * MIME/path matches and uncalculated durations bypass the duration floor and are left to
 * playback capability checks and TagLib metadata readers.
 */
fun buildLocalAudioSelection(minDurationMs: Int): Pair<String, Array<String>> {
    val clampedMinDurationMs = minDurationMs.coerceAtLeast(0)
    val mimePlaceholders = INCOMPLETE_DURATION_MIME_SELECTION_ARGS.joinToString(",") { "?" }
    val extensionSelection = INCOMPLETE_DURATION_EXTENSION_SELECTION_ARGS.joinToString(" OR ") {
        "LOWER(${MediaStore.Audio.Media.DATA}) LIKE ?"
    }
    val selection = buildString {
        append("(")
        append("${MediaStore.Audio.Media.DURATION} >= ?")
        append(" OR COALESCE(${MediaStore.Audio.Media.DURATION}, 0) <= 0")
        append(" OR LOWER(COALESCE(${MediaStore.Audio.Media.MIME_TYPE}, '')) IN ($mimePlaceholders)")
        append(" OR $extensionSelection")
        append(")")
        append(" AND COALESCE(${MediaStore.Audio.Media.TITLE}, '') != ''")
        append(" AND ${MediaStore.Audio.Media.DATA} IS NOT NULL")
    }
    return selection to arrayOf(clampedMinDurationMs.toString()) +
        INCOMPLETE_DURATION_MIME_SELECTION_ARGS +
        INCOMPLETE_DURATION_EXTENSION_SELECTION_ARGS
}
