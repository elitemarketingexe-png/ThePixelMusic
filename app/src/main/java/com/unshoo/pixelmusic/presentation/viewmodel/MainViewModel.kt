package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.worker.SyncManager
import com.unshoo.pixelmusic.data.worker.SyncProgress
import com.unshoo.pixelmusic.utils.LogUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository

@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncManager: SyncManager,
    private val datastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    musicRepository: MusicRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private var lastKnownCookie: String? = null
    private var profileRefreshJob: Job? = null

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            playlistPreferencesRepository.pruneExpiredPlaylists()
        }
        viewModelScope.launch {
            datastoreRepository.cookies
                .map { it.toRawCookie() }
                .distinctUntilChanged()
                .collect { rawCookie ->
                    val isInitialValue = lastKnownCookie == null
                    unshoo.ianshulyadav.pixelmusic.innertube.YouTube.cookie = rawCookie
                    lastKnownCookie = rawCookie
                    if (rawCookie.isNotEmpty()) {
                        refreshProfile(rawCookie, delayForColdStart = isInitialValue)
                    } else {
                        profileRefreshJob?.cancel()
                        profileRefreshJob = null
                        datastoreRepository.saveYtProfile("", "", "")
                    }
                }
        }
        viewModelScope.launch {
            datastoreRepository.dataSyncId.collect { id ->
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.dataSyncId = id
                LogUtils.d(this@MainViewModel, "MainViewModel: Syncing dataSyncId to YouTube singleton.")
            }
        }
    }

    private fun refreshProfile(cookie: String, delayForColdStart: Boolean) {
        profileRefreshJob?.cancel()
        profileRefreshJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.unshoo.pixelmusic.utils.AppReadinessSignal.awaitReady()
            if (delayForColdStart) delay(10_000L)
            if (lastKnownCookie != cookie) return@launch
            try {
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.accountInfo()
                    .onSuccess { info ->
                        datastoreRepository.saveYtProfile(
                            name = info.name,
                            handle = info.channelHandle ?: "",
                            avatarUrl = info.thumbnailUrl ?: ""
                        )
                    }
                    .onFailure { error ->
                        LogUtils.e(this@MainViewModel, error, "Failed to fetch YouTube account info")
                    }
            } catch (error: Exception) {
                LogUtils.e(this@MainViewModel, error, "Error fetching YouTube account info")
            }
        }
    }

    val isSetupComplete: StateFlow<Boolean?> = userPreferencesRepository.initialSetupDoneFlow
        .map { it as Boolean? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val hasCompletedInitialSync: StateFlow<Boolean> = userPreferencesRepository.lastSyncTimestampFlow
        .map { it > 0L }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true // 乐观策略：默认已同步
        )

    /**
     * Un Flow que emite `true` si el SyncWorker está encolado o en ejecución.
     * Ideal para mostrar un indicador de carga.
     */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * Flow that exposes detailed sync progress including file count and phase.
     */
    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    /**
     * Un Flow que emite `true` si la base de datos de Room no tiene canciones.
     * Nos ayuda a saber si es la primera vez que se abre la app.
     */
    val isLibraryEmpty: StateFlow<Boolean> = musicRepository
        .getSongCountFlow()
        .map { it == 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Función para iniciar la sincronización de la biblioteca de música.
     * Se debe llamar después de que los permisos hayan sido concedidos.
     */
    fun startSync() {
        LogUtils.i(this, "startSync called")
        viewModelScope.launch {
            if (isSetupComplete.value == true) {
                com.unshoo.pixelmusic.utils.AppReadinessSignal.awaitReady()
                syncManager.start()
            }
        }
    }
}
