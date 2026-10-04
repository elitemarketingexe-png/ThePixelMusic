package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.update.AppReleaseAsset
import com.unshoo.pixelmusic.data.model.update.AppReleaseInfo
import com.unshoo.pixelmusic.data.model.update.UpdateCheckResult
import com.unshoo.pixelmusic.data.notification.AppUpdateNotificationManager
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.AppUpdateRepository
import com.unshoo.pixelmusic.data.update.AppUpdateDownloadManager
import com.unshoo.pixelmusic.data.worker.AppUpdateScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    val downloadManager: AppUpdateDownloadManager,
    private val appUpdateScheduler: AppUpdateScheduler,
    private val notificationManager: AppUpdateNotificationManager
) : ViewModel() {

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _updateResult = MutableStateFlow<UpdateCheckResult?>(null)
    val updateResult: StateFlow<UpdateCheckResult?> = _updateResult.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    private val _activeRelease = MutableStateFlow<AppReleaseInfo?>(null)
    val activeRelease: StateFlow<AppReleaseInfo?> = _activeRelease.asStateFlow()

    private val _activeTargetAsset = MutableStateFlow<AppReleaseAsset?>(null)
    val activeTargetAsset: StateFlow<AppReleaseAsset?> = _activeTargetAsset.asStateFlow()

    val autoUpdateCheckEnabled: StateFlow<Boolean> =
        userPreferencesRepository.autoUpdateCheckEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val lastUpdateCheckTime: StateFlow<Long> =
        userPreferencesRepository.lastUpdateCheckTimeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    /**
     * Checks for updates from GitHub repository.
     */
    fun checkForUpdates(force: Boolean = true) {
        if (_isChecking.value) return

        viewModelScope.launch {
            _isChecking.value = true
            _updateResult.value = null

            val result = appUpdateRepository.checkForUpdate(force = force)
            _updateResult.value = result
            _isChecking.value = false

            if (result is UpdateCheckResult.Available) {
                _activeRelease.value = result.release
                _activeTargetAsset.value = result.targetAsset
                _showUpdateSheet.value = true
            }
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoUpdateCheckEnabled(enabled)
            if (enabled) {
                appUpdateScheduler.schedulePeriodicCheck()
            } else {
                appUpdateScheduler.cancelPeriodicCheck()
            }
        }
    }

    fun ignoreVersion(version: String) {
        viewModelScope.launch {
            userPreferencesRepository.setIgnoredUpdateVersion(version)
            _showUpdateSheet.value = false
            notificationManager.dismissNotification()
        }
    }

    fun dismissUpdateSheet() {
        _showUpdateSheet.value = false
    }

    fun openUpdateSheet(release: AppReleaseInfo, targetAsset: AppReleaseAsset?) {
        _activeRelease.value = release
        _activeTargetAsset.value = targetAsset
        _showUpdateSheet.value = true
    }

    fun resetResult() {
        _updateResult.value = null
    }
}
