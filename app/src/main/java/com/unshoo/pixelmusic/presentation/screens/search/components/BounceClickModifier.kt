package com.unshoo.pixelmusic.presentation.screens.search.components

import androidx.compose.ui.Modifier
import com.unshoo.pixelmusic.presentation.components.bounceClick as sharedBounceClick

/**
 * Re-exported for backwards compatibility with search package.
 */
fun Modifier.bounceClick(
    scaleDown: Float = 0.95f,
    onClick: () -> Unit
): Modifier = this.sharedBounceClick(scaleDown = scaleDown, onClick = onClick)

