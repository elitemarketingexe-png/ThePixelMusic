package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

// Expressive motion physics and emphasized cubic bezier easing curves
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

const val TRANSITION_DURATION = 450

// Hierarchical Navigation (Push / Pop between sub-screens)
fun enterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

fun exitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

fun popEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.95f
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
)

fun popExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.5f).toInt() }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

// Fast responsive settings dismiss transitions
private const val SETTINGS_DISMISS_TOTAL_MS = 280
private const val SETTINGS_DISMISS_FADE_MS = 220

fun settingsPopExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.80f).toInt() }
) + scaleOut(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetScale = 0.97f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedAccelerateEasing)
)

fun settingsPopEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.12f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.98f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = SETTINGS_DISMISS_FADE_MS, easing = EmphasizedDecelerateEasing)
)



