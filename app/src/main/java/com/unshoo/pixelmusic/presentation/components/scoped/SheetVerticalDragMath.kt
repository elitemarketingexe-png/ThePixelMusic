package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.ui.util.lerp
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState
import kotlin.math.abs

internal data class SheetVerticalDragFrame(
    val translationY: Float,
    val expansionFraction: Float
)

internal fun computeSheetVerticalDragFrame(
    currentTranslationY: Float,
    dragAmount: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float
): SheetVerticalDragFrame {
    val newY = (currentTranslationY + dragAmount)
        .coerceIn(
            expandedY - miniHeightPx * 0.2f,
            collapsedY + miniHeightPx * 0.2f
        )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return SheetVerticalDragFrame(
        translationY = newY,
        expansionFraction = newFraction
    )
}

internal fun resolveVerticalSheetTargetState(
    currentSheetContentState: PlayerSheetState,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float
): PlayerSheetState {
    return when {
        currentSheetContentState == PlayerSheetState.EXPANDED &&
            accumulatedDragY <= 0f -> PlayerSheetState.EXPANDED

        abs(accumulatedDragY) > minDragThresholdPx ->
            if (accumulatedDragY < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        abs(verticalVelocity) > velocityThreshold ->
            if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED

        else ->
            if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
    }
}

internal fun collapseAnimationDurationForFraction(currentFraction: Float): Int {
    // Shorter, deterministic close animations feel smoother on midrange devices than
    // low-stiffness springs, especially while lists/pages are still settling after scroll.
    return lerp(170f, 235f, currentFraction.coerceIn(0f, 1f)).toInt()
}

internal fun collapseInitialSquashForFraction(currentFraction: Float): Float {
    // Keep the old "squish" extremely subtle. Large/bouncy scale changes are visually
    // expensive and made dismiss look laggy on low-end GPUs.
    return lerp(1.0f, 0.992f, currentFraction.coerceIn(0f, 1f))
}
