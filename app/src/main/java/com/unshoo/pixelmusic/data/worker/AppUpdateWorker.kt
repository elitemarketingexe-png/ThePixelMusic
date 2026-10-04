package com.unshoo.pixelmusic.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unshoo.pixelmusic.data.model.update.UpdateCheckResult
import com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.AppUpdateRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class AppUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val appUpdateRepository: AppUpdateRepository,
    private val notificationManager: AppUpdateNotificationManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "pixelmusic_app_update_worker"
        const val PERIODIC_WORK_NAME = "pixelmusic_app_update_periodic_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val autoCheckEnabled = userPreferencesRepository.autoUpdateCheckEnabledFlow.first()
        if (!autoCheckEnabled) {
            Timber.d("AppUpdateWorker: Auto update check is disabled by user.")
            return@withContext Result.success()
        }

        Timber.d("AppUpdateWorker: Checking for latest release from GitHub...")
        when (val result = appUpdateRepository.checkForUpdate(force = false)) {
            is UpdateCheckResult.Available -> {
                val ignoredVersion = userPreferencesRepository.ignoredUpdateVersionFlow.first()
                if (ignoredVersion.equals(result.release.tagName, ignoreCase = true)) {
                    Timber.d("AppUpdateWorker: Version ${result.release.tagName} is ignored by user.")
                    return@withContext Result.success()
                }

                Timber.i("AppUpdateWorker: New release available: ${result.release.tagName}. Showing notification.")
                notificationManager.showUpdateNotification(result.release, result.targetAsset)
                Result.success()
            }
            is UpdateCheckResult.UpToDate -> {
                Timber.d("AppUpdateWorker: App is up to date (${result.currentVersion}).")
                Result.success()
            }
            is UpdateCheckResult.Error -> {
                Timber.w("AppUpdateWorker: Check failed: ${result.message}")
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
}
