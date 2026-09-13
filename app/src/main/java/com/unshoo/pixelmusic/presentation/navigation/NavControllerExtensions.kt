package com.unshoo.pixelmusic.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

// Lifecycle gate for navigation controller extensions.
//
// Two behaviors were intentionally refined:
//
// 1. `lifecycle == null -> false`. Returns false when there is no current
//    back stack entry; navigating with a null entry (during graph setup races) is
//    exactly the case the gate exists to catch.
//
// 2. The `currentDestination?.route == targetRoute` block. `currentDestination?.route`
//    is the route *pattern* (e.g. "settings_category/{categoryId}") while callers pass
//    fully-built routes ("settings_category/appearance"), so the check silently never
//    fired for parameterized destinations; for static routes it ate legitimate taps
//    (e.g. re-opening a section you just left) and produced "dead tap" reports.
//    Deduplication of rapid double navigation is already handled correctly by
//    `launchSingleTop = true` below and by the ScreenWrapper input gate, which stops
//    touches from reaching the screen that is being popped.
private fun NavController.isReadyForNavigation(): Boolean {
    return runCatching {
        // We allow navigation if the current entry is at least STARTED.
        // This is safer than strictly RESUMED as transitions can sometimes delay RESUMED state.
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    }.getOrDefault(false)
}

/**
 * Back-press equivalent of [navigateSafely]: only pops while the current entry is
 * at least STARTED, so a second back press that sneaks in during the pop transition
 * cannot pop a *second* destination (the classic "double back -> blank screen" bug).
 */
fun NavController.popBackStackSafely(): Boolean {
    val lifecycle = currentBackStackEntry?.lifecycle
    if (lifecycle != null && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
    return try {
        popBackStack()
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe popBackStack failed", e)
        false
    }
}

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    if (isMainRootRoute(route)) {
        return navigateToTopLevelSafely(route)
    }
    return try {
        navigate(route) {
            launchSingleTop = true
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation failed for route: $route", e)
        false
    }
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForNavigation()) return false
    return try {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation failed for route: $route", e)
        false
    }
}

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForNavigation()) return false
    return try {
        navigate(route) {
            launchSingleTop = false
            popUpTo(patternToPop) {
                inclusive = true
            }
            builder()
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation replacing failed for route: $route", e)
        false
    }
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    return try {
        navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe top-level navigation failed for route: $route", e)
        false
    }
}
