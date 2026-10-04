package com.unshoo.pixelmusic.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.unshoo.pixelmusic.data.model.update.UpdateCheckResult
import com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.AppUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val notificationManager: AppUpdateNotificationManager
) {
    companion object {
        private const val STARTUP_CHECK_MIN_INTERVAL_MS = 24L * 60L * 60L * 1000L // 24 hours
    }

    /**
     * Enqueues a periodic WorkManager task that checks for updates once every 24 hours.
     */
    fun schedulePeriodicCheck() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            24, TimeUnit.HOURS,
            4, TimeUnit.HOURS // Flex interval
        )
            .setConstraints(constraints)
            .addTag(AppUpdateWorker.WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AppUpdateWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
        Timber.d("AppUpdateScheduler: Periodic update check scheduled every 24h.")
    }

    /**
     * Cancels the periodic background check.
     */
    fun cancelPeriodicCheck() {
        workManager.cancelUniqueWork(AppUpdateWorker.PERIODIC_WORK_NAME)
        Timber.d("AppUpdateScheduler: Periodic update check cancelled.")
    }

    /**
     * Checks for updates during app startup if more than 24h has elapsed since last check.
     * Runs asynchronously on [scope] and does not block the main thread.
     */
    fun checkOnStartupIfNeeded(scope: CoroutineScope) {
        scope.launch {
            try {
                val autoCheckEnabled = userPreferencesRepository.autoUpdateCheckEnabledFlow.first()
                if (!autoCheckEnabled) return@launch

                val lastCheck = userPreferencesRepository.lastUpdateCheckTimeFlow.first()
                val now = System.currentTimeMillis()
                if (now - lastCheck < STARTUP_CHECK_MIN_INTERVAL_MS) {
                    Timber.d("AppUpdateScheduler: Startup check skipped (checked recently).")
                    return@launch
                }

                Timber.d("AppUpdateScheduler: Performing startup update check...")
                when (val result = appUpdateRepository.checkForUpdate(force = false)) {
                    is UpdateCheckResult.Available -> {
                        val ignored = userPreferencesRepository.ignoredUpdateVersionFlow.first()
                        if (!ignored.equals(result.release.tagName, ignoreCase = true)) {
                            Timber.i("AppUpdateScheduler: Startup check found update ${result.release.tagName}.")
                            notificationManager.showUpdateNotification(result.release, result.targetAsset)
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Timber.w(e, "AppUpdateScheduler: Startup update check failed (non-fatal)")
            }
        }
    }
}
