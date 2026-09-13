package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.gdrive.GDriveRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.drinkless.tdlib.TdApi

enum class ExternalServiceAccount {
    TELEGRAM,
    GOOGLE_DRIVE,
    YOUTUBE,
    LASTFM
}

data class ExternalAccountUiModel(
    val service: ExternalServiceAccount,
    val title: String,
    val accountLabel: String,
    val syncedContentLabel: String,
    val isLoggingOut: Boolean,
    val isSyncEnabled: Boolean = true
)

data class AccountsUiState(
    val connectedAccounts: List<ExternalAccountUiModel> = emptyList(),
    val disconnectedServices: List<ExternalServiceAccount> = emptyList(),
    val userName: String? = null
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository,
    private val gDriveRepository: GDriveRepository,
    private val datastoreRepository: com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository,
    private val syncManager: com.unshoo.pixelmusic.data.worker.SyncManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val isSyncing = syncManager.isSyncing

    val syncEnabled: StateFlow<Boolean> = datastoreRepository.syncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun syncLibrary() {
        viewModelScope.launch {
            syncManager.forceRefresh(syncCloud = true)
        }
    }

    val youtubeSyncPlaylistsAndLikes: StateFlow<Boolean> = userPreferencesRepository.youtubeSyncPlaylistsAndLikesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val youtubeSyncListeningActivity: StateFlow<Boolean> = userPreferencesRepository.youtubeSyncListeningActivityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val youtubePersonalizedExplore: StateFlow<Boolean> = userPreferencesRepository.youtubePersonalizedExploreFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val youtubePersonalizedQueue: StateFlow<Boolean> = userPreferencesRepository.youtubePersonalizedQueueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun toggleYoutubeSyncPlaylistsAndLikes(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setYoutubeSyncPlaylistsAndLikes(enabled)
        }
    }

    fun toggleYoutubeSyncListeningActivity(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setYoutubeSyncListeningActivity(enabled)
        }
    }

    fun toggleYoutubePersonalizedExplore(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setYoutubePersonalizedExplore(enabled)
        }
    }

    fun toggleYoutubePersonalizedQueue(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setYoutubePersonalizedQueue(enabled)
        }
    }

    private val loggingOutServices = MutableStateFlow<Set<ExternalServiceAccount>>(emptySet())

    private val telegramStateFlow = combine(
        telegramRepository.authorizationState
            .map { it is TdApi.AuthorizationStateReady }
            .distinctUntilChanged(),
        musicRepository.getAllTelegramChannels().map { it.size }
    ) { connected, channelCount ->
        connected to channelCount
    }

    private val gDriveStateFlow = combine(
        gDriveRepository.isLoggedInFlow,
        gDriveRepository.getFolders().map { it.size }
    ) { connected, folderCount ->
        connected to folderCount
    }

    private val youtubeStateFlow = combine(
        datastoreRepository.cookies.map { it.toRawCookie().isNotEmpty() }.distinctUntilChanged(),
        com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context).playlistRepository().observeAll().map { it.size }
    ) { connected, playlistCount ->
        connected to playlistCount
    }

    private val lastfmStateFlow = combine(
        userPreferencesRepository.lastfmSessionFlow,
        userPreferencesRepository.lastfmUsernameFlow,
        userPreferencesRepository.lastfmScrobblingEnabledFlow
    ) { session, username, enabled ->
        Triple(session.isNotEmpty(), username, enabled)
    }

    private val telegramUsernameFlow = telegramRepository.authorizationState
        .map { state ->
            if (state is TdApi.AuthorizationStateReady) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(5000) {
                        val me = telegramRepository.getMe()
                        me?.firstName?.trim()?.takeIf { it.isNotEmpty() }
                    }
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
        .onStart { emit(null) }

    val uiState: StateFlow<AccountsUiState> = combine(
        combine(
            listOf(
                telegramStateFlow,
                gDriveStateFlow,
                youtubeStateFlow,
                lastfmStateFlow
            )
        ) { it.toList() },
        loggingOutServices,
        datastoreRepository.ytUsername,
        telegramUsernameFlow,
        datastoreRepository.syncEnabled
    ) { states, activeLogouts, ytName, tgName, ytSyncEnabled ->
        val (telegramConnected, telegramChannelCount) = (states.getOrNull(0) as? Pair<*, *>)?.let { (it.first as? Boolean ?: false) to (it.second as? Int ?: 0) } ?: (false to 0)
        val (gDriveConnected, gDriveFolderCount) = (states.getOrNull(1) as? Pair<*, *>)?.let { (it.first as? Boolean ?: false) to (it.second as? Int ?: 0) } ?: (false to 0)
        val (youtubeConnected, youtubePlaylistCount) = (states.getOrNull(2) as? Pair<*, *>)?.let { (it.first as? Boolean ?: false) to (it.second as? Int ?: 0) } ?: (false to 0)
        val lastfmState = states.getOrNull(3) as? Triple<*, *, *>
        val lastfmConnected = lastfmState?.first as? Boolean ?: false
        val lastfmUsername = lastfmState?.second as? String ?: ""
        val lastfmScrobbleEnabled = lastfmState?.third as? Boolean ?: false

        val calculatedUserName = when {
            gDriveConnected && !gDriveRepository.userDisplayName.isNullOrBlank() -> gDriveRepository.userDisplayName
            youtubeConnected && ytName.isNotBlank() -> ytName
            telegramConnected && !tgName.isNullOrBlank() -> tgName
            else -> null
        }

        val connectedAccounts = buildList {
            if (telegramConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.TELEGRAM,
                        title = "Telegram",
                        accountLabel = if (!tgName.isNullOrBlank()) tgName else "Active Telegram session",
                        syncedContentLabel = formatCount(
                            count = telegramChannelCount,
                            singular = "synced channel",
                            plural = "synced channels"
                        ),
                        isLoggingOut = ExternalServiceAccount.TELEGRAM in activeLogouts
                    )
                )
            }
            if (gDriveConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.GOOGLE_DRIVE,
                        title = "Google Drive",
                        accountLabel = gDriveRepository.userDisplayName
                            ?.takeIf { it.isNotBlank() }
                            ?: gDriveRepository.userEmail
                                ?.takeIf { it.isNotBlank() }
                            ?: "Google account connected",
                        syncedContentLabel = formatCount(
                            count = gDriveFolderCount,
                            singular = "synced folder",
                            plural = "synced folders"
                        ),
                        isLoggingOut = ExternalServiceAccount.GOOGLE_DRIVE in activeLogouts
                    )
                )
            }
            if (youtubeConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.YOUTUBE,
                        title = "YouTube Client",
                        accountLabel = if (ytName.isNotBlank()) ytName else "YouTube session connected",
                        syncedContentLabel = if (ytSyncEnabled) formatCount(
                            count = youtubePlaylistCount,
                            singular = "synced playlist",
                            plural = "synced playlists"
                        ) else "Sync Paused",
                        isLoggingOut = ExternalServiceAccount.YOUTUBE in activeLogouts,
                        isSyncEnabled = ytSyncEnabled
                    )
                )
            }
            if (lastfmConnected) {
                add(
                    ExternalAccountUiModel(
                        service = ExternalServiceAccount.LASTFM,
                        title = "Last.fm",
                        accountLabel = if (lastfmUsername.isNotBlank()) lastfmUsername else "Last.fm session connected",
                        syncedContentLabel = if (lastfmScrobbleEnabled) "Scrobbling enabled" else "Scrobbling disabled",
                        isLoggingOut = ExternalServiceAccount.LASTFM in activeLogouts
                    )
                )
            }
        }

        val disconnectedServices = buildList {
            if (!telegramConnected) add(ExternalServiceAccount.TELEGRAM)
            if (!gDriveConnected) add(ExternalServiceAccount.GOOGLE_DRIVE)
            if (!youtubeConnected) add(ExternalServiceAccount.YOUTUBE)
            if (!lastfmConnected) add(ExternalServiceAccount.LASTFM)
        }

        AccountsUiState(
            connectedAccounts = connectedAccounts,
            disconnectedServices = disconnectedServices,
            userName = calculatedUserName
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun logout(service: ExternalServiceAccount) {
        if (service in loggingOutServices.value) return

        viewModelScope.launch {
            loggingOutServices.update { it + service }
            try {
                runCatching {
                    when (service) {
                        ExternalServiceAccount.TELEGRAM -> {
                            telegramRepository.logout()
                            telegramRepository.clearMemoryCache()
                            musicRepository.clearTelegramData()
                        }
                        ExternalServiceAccount.GOOGLE_DRIVE -> gDriveRepository.logout()
                        ExternalServiceAccount.YOUTUBE -> {
                            datastoreRepository.saveCookies(com.unshoo.pixelmusic.data.model.youtube.Cookies(""))
                            datastoreRepository.saveDataSyncId("")
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()
                            android.webkit.WebStorage.getInstance().deleteAllData()
                        }
                        ExternalServiceAccount.LASTFM -> {
                            userPreferencesRepository.setLastfmSession("")
                            userPreferencesRepository.setLastfmUsername("")
                            userPreferencesRepository.setLastfmApiKey("")
                            userPreferencesRepository.setLastfmApiSecret("")
                            com.unshoo.pixelmusic.data.lastfm.LastFM.sessionKey = null
                            com.unshoo.pixelmusic.data.lastfm.LastFM.initialize(
                                apiKey = com.unshoo.pixelmusic.BuildConfig.LASTFM_API_KEY,
                                secret = com.unshoo.pixelmusic.BuildConfig.LASTFM_SECRET
                            )
                        }
                    }
                }
            } finally {
                loggingOutServices.update { it - service }
            }
        }
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return if (count == 1) {
            "1 $singular"
        } else {
            "$count $plural"
        }
    }
}
