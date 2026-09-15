package com.unshoo.pixelmusic.presentation.utils

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.unshoo.pixelmusic.presentation.model.AppLauncherIcon
import timber.log.Timber

object AppIconManager {

    private const val TAG = "AppIconManager"
    private const val PREFS_NAME = "pixelmusic_app_icon_prefs"
    private const val KEY_ACTIVE_ICON_ID = "active_icon_id"

    /**
     * Determines which [AppLauncherIcon] alias is currently active.
     * Checks:
     * 1. Activity launch intent component (instantaneous)
     * 2. Synchronous SharedPreferences (instantaneous on cold start)
     * 3. System PackageManager component enabled state
     * 4. [fallbackIconId] or [AppLauncherIcon.DEFAULT]
     */
    fun getActiveLauncherIcon(context: Context, fallbackIconId: String? = null): AppLauncherIcon {
        // 1. Check if the Activity intent explicitly targeted a specific alias
        if (context is Activity) {
            val componentClass = context.intent?.component?.className
            if (!componentClass.isNullOrBlank()) {
                val matched = AppLauncherIcon.entries.find { it.aliasClassName == componentClass }
                if (matched != null) {
                    saveActiveIconToPrefs(context, matched)
                    return matched
                }
            }
        }

        // 2. Synchronous persistent SharedPreferences check (instantaneous on main thread / cold start)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getString(KEY_ACTIVE_ICON_ID, null)
        if (!savedId.isNullOrBlank()) {
            return AppLauncherIcon.fromId(savedId)
        }

        // 3. Fallback to querying PackageManager
        val pm = context.packageManager
        val packageName = context.packageName

        for (icon in AppLauncherIcon.entries) {
            val component = ComponentName(packageName, icon.aliasClassName)
            val state = pm.getComponentEnabledSetting(component)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                saveActiveIconToPrefs(context, icon)
                return icon
            }
        }

        // 4. Fallback icon ID if provided
        if (!fallbackIconId.isNullOrBlank()) {
            return AppLauncherIcon.fromId(fallbackIconId)
        }

        return AppLauncherIcon.DEFAULT
    }

    private fun saveActiveIconToPrefs(context: Context, icon: AppLauncherIcon) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_ICON_ID, icon.id)
                .commit()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to persist active icon to SharedPreferences")
        }
    }

    /**
     * Updates the Android Overview / Recents card icon to match [icon].
     */
    fun updateTaskDescription(activity: Activity, icon: AppLauncherIcon) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                activity.setTaskDescription(
                    ActivityManager.TaskDescription.Builder()
                        .setIcon(icon.iconRes)
                        .build()
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to update TaskDescription for $icon")
            }
        }
    }

    /**
     * Idempotently switches the launcher icon to [newIcon].
     *
     * If [newIcon] is already the active launcher alias, no operations are performed.
     * Otherwise:
     * 1. Enables the target [newIcon] alias with PackageManager.DONT_KILL_APP
     * 2. Disables the previous active alias(es) with PackageManager.DONT_KILL_APP
     * 3. Synchronizes TaskDescription on caller Activity if provided
     *
     * @return true if an actual icon change was executed, false if already active or failed.
     */
    fun setLauncherIcon(context: Context, newIcon: AppLauncherIcon): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        val currentIcon = getActiveLauncherIcon(context)

        if (currentIcon == newIcon) {
            Timber.tag(TAG).d("Requested icon $newIcon is already active, skipping")
            if (context is Activity) {
                updateTaskDescription(context, newIcon)
            }
            return false
        }

        return try {
            val targetComponent = ComponentName(packageName, newIcon.aliasClassName)

            // Enable the new alias first so there is never a window where no launcher activity exists
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable other aliases
            for (icon in AppLauncherIcon.entries) {
                if (icon != newIcon) {
                    val otherComponent = ComponentName(packageName, icon.aliasClassName)
                    pm.setComponentEnabledSetting(
                        otherComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            if (context is Activity) {
                updateTaskDescription(context, newIcon)
            }

            saveActiveIconToPrefs(context, newIcon)
            Timber.tag(TAG).i("Successfully switched launcher icon from $currentIcon to $newIcon")
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to switch launcher icon to $newIcon")
            false
        }
    }
}
