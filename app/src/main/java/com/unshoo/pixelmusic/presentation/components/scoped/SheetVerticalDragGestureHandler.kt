package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Encapsulates vertical drag gesture state and target resolution for the player sheet.
 * Behavior is kept identical to the previous inline implementation.
 */
internal class SheetVerticalDragGestureHandler(
    private val scope: CoroutineScope,
    private val velocityTracker: VelocityTracker,
    private val densityProvider: () -> Density,
    private val sheetMotionController: SheetMotionController,
    private val playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    private val currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    private val expandedYProvider: () -> Float,
    private val collapsedYProvider: () -> Float,
    private val miniHeightPxProvider: () -> Float,
    private val currentSheetStateProvider: () -> PlayerSheetState,
    private val visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    private val onDraggingChange: (Boolean) -> Unit,
    private val onDraggingPlayerAreaChange: (Boolean) -> Unit,
    private val onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    private val onExpandSheetState: () -> Unit,
    private val onCollapseSheetState: () -> Unit
) {
    private var initialFractionOnDragStart = 0f
    private var initialYOnDragStart = 0f
    private var accumulatedDragYSinceStart = 0f
    private var lastSnappedY = Float.NaN
    private var lastSnappedFraction = Float.NaN
    private var dragSnapJob: Job? = null

    fun onDragStart() {
        dragSnapJob?.cancel()
        dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sheetMotionController.stop()
        }
        onDraggingChange(true)
        onDraggingPlayerAreaChange(true)
        velocityTracker.resetTracking()
        initialFractionOnDragStart = playerContentExpansionFraction.value
        initialYOnDragStart = currentSheetTranslationY.value
        accumulatedDragYSinceStart = 0f
        lastSnappedY = initialYOnDragStart
        lastSnappedFraction = initialFractionOnDragStart
    }

    fun onVerticalDrag(
        uptimeMillis: Long,
        position: Offset,
        dragAmount: Float
    ) {
        accumulatedDragYSinceStart += dragAmount
        val dragFrame = computeSheetVerticalDragFrame(
            currentTranslationY = currentSheetTranslationY.value,
            dragAmount = dragAmount,
            expandedY = expandedYProvider(),
            collapsedY = collapsedYProvider(),
            miniHeightPx = miniHeightPxProvider(),
            initialFractionOnDragStart = initialFractionOnDragStart,
            initialYOnDragStart = initialYOnDragStart
        )
        // Avoid dispatching work for tiny pointer deltas. Scroll/fling can deliver many
        // sub-pixel moves; snapping each one steals frame budget from the page underneath.
        if (abs(dragFrame.translationY - lastSnappedY) >= 0.5f ||
            abs(dragFrame.expansionFraction - lastSnappedFraction) >= 0.0015f
        ) {
            val tY = dragFrame.translationY
            val eF = dragFrame.expansionFraction
            lastSnappedY = tY
            lastSnappedFraction = eF
            if (dragSnapJob?.isActive != true) {
                dragSnapJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    sheetMotionController.snapToDirect(
                        translationYValue = tY,
                        expansionFractionValue = eF
                    )
                }
            }
        }
        velocityTracker.addPosition(uptimeMillis, position)
    }

    fun onDragEnd() {
        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)

        val verticalVelocity = velocityTracker.calculateVelocity().y
        val currentFraction = playerContentExpansionFraction.value
        val minDragThresholdPx = with(densityProvider()) { 5.dp.toPx() }
        val velocityThreshold = 150f

        val targetState = resolveVerticalSheetTargetState(
            currentSheetContentState = currentSheetStateProvider(),
            accumulatedDragY = accumulatedDragYSinceStart,
            minDragThresholdPx = minDragThresholdPx,
            verticalVelocity = verticalVelocity,
            velocityThreshold = velocityThreshold,
            currentFraction = currentFraction
        )

        scope.launch {
            if (targetState == PlayerSheetState.EXPANDED) {
                launch {
                    onAnimateSheet(true, null, 0f)
                }
                onExpandSheetState()
            } else {
                val closeSpec = tween<Float>(
                    durationMillis = collapseAnimationDurationForFraction(currentFraction),
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
                val clampedVelocity = verticalVelocity.coerceIn(-2600f, 2600f)
                // No scale squash — keep it at 1f to avoid any secondary bounce.
                visualOvershootScaleY.snapTo(1f)
                onAnimateSheet(
                    false,
                    closeSpec,
                    clampedVelocity
                )
                onCollapseSheetState()
            }
        }

        accumulatedDragYSinceStart = 0f
    }

    fun onDragCancel() {
        dragSnapJob?.cancel()
        dragSnapJob = null
        onDraggingChange(false)
        onDraggingPlayerAreaChange(false)
        accumulatedDragYSinceStart = 0f

        val restoreExpanded = currentSheetStateProvider() == PlayerSheetState.EXPANDED
        scope.launch {
            visualOvershootScaleY.snapTo(1f)
            onAnimateSheet(
                restoreExpanded,
                tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                0f
            )
        }
    }
}

internal fun Modifier.playerSheetVerticalDragGesture(
    enabled: Boolean,
    handler: SheetVerticalDragGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectVerticalDragGestures(
            onDragStart = { handler.onDragStart() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                handler.onVerticalDrag(
                    uptimeMillis = change.uptimeMillis,
                    position = change.position,
                    dragAmount = dragAmount
                )
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
