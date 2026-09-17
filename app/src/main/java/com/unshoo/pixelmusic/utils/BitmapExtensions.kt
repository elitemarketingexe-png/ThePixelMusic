package com.unshoo.pixelmusic.utils

import android.graphics.Bitmap
import android.os.Build

/**
 * Returns a software-backed [Bitmap] safe for pixel access, [android.graphics.Canvas] drawing,
 * [androidx.palette.graphics.Palette] extraction, or software blur operations.
 *
 * Bitmaps with [Bitmap.Config.HARDWARE] live in graphics memory (VRAM) and will throw
 * [IllegalArgumentException] if passed to software rendering pipelines or pixel readers.
 * This helper safely copies hardware bitmaps to [Bitmap.Config.ARGB_8888] when needed.
 */
fun Bitmap.toSoftwareBitmap(): Bitmap {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false) ?: this
    } else {
        this
    }
}
