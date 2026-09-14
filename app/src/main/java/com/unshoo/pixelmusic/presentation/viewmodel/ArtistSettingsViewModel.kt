package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistSettingsUiState(
    val artistDelimiters: List<String> = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
    val wordDelimiters: List<String> = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS,
    val filterKeywords: List<String> = UserPreferencesRepository.DEFAULT_FILTER_KEYWORDS,
    val extractArtistsFromTitle: Boolean = true,
    val groupByAlbumArtist: Boolean = false,
    val rescanRequired: Boolean = false,
    val isResyncing: Boolean = false
)

@HiltViewModel
class ArtistSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistSettingsUiState())
    val uiState: StateFlow<ArtistSettingsUiState> = _uiState.asStateFlow()

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.artistDelimitersFlow.collect { delimiters ->
                _uiState.update { it.copy(artistDelimiters = delimiters) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.artistWordDelimitersFlow.collect { delimiters ->
                _uiState.update { it.copy(wordDelimiters = delimiters) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.filterKeywordsFlow.collect { keywords ->
                _uiState.update { it.copy(filterKeywords = keywords) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.extractArtistsFromTitleFlow.collect { enabled ->
                _uiState.update { it.copy(extractArtistsFromTitle = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.groupByAlbumArtistFlow.collect { enabled ->
                _uiState.update { it.copy(groupByAlbumArtist = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.artistSettingsRescanRequiredFlow.collect { required ->
                _uiState.update { it.copy(rescanRequired = required) }
            }
        }

        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isResyncing = syncing) }
            }
        }
    }

    fun setGroupByAlbumArtist(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setGroupByAlbumArtist(enabled)
        }
    }

    fun setArtistDelimiters(delimiters: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(delimiters)
        }
    }

    fun addDelimiter(delimiter: String): Boolean {
        val trimmed = delimiter.trim()
        if (trimmed.isEmpty()) return false
        
        val current = _uiState.value.artistDelimiters
        if (current.contains(trimmed)) return false
        
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(current + trimmed)
        }
        return true
    }

    fun removeDelimiter(delimiter: String) {
        val current = _uiState.value.artistDelimiters
        if (current.size <= 1) return // Keep at least one delimiter
        
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(current - delimiter)
        }
    }

    fun resetDelimitersToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetArtistDelimitersToDefault()
        }
    }

    fun addWordDelimiter(delimiter: String): Boolean {
        val trimmed = delimiter.trim()
        if (trimmed.isEmpty()) return false

        val current = _uiState.value.wordDelimiters
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false

        viewModelScope.launch {
            userPreferencesRepository.setArtistWordDelimiters(current + trimmed)
        }
        return true
    }

    fun removeWordDelimiter(delimiter: String) {
        val current = _uiState.value.wordDelimiters
        viewModelScope.launch {
            userPreferencesRepository.setArtistWordDelimiters(current - delimiter)
        }
    }

    fun resetWordDelimitersToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetArtistWordDelimitersToDefault()
        }
    }

    fun addFilterKeyword(keyword: String): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return false

        val current = _uiState.value.filterKeywords
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false

        viewModelScope.launch {
            userPreferencesRepository.setFilterKeywords(current + trimmed)
        }
        return true
    }

    fun removeFilterKeyword(keyword: String) {
        val current = _uiState.value.filterKeywords
        viewModelScope.launch {
            userPreferencesRepository.setFilterKeywords(current.filterNot { it.equals(keyword, ignoreCase = true) })
        }
    }

    fun resetFilterKeywordsToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetFilterKeywordsToDefault()
        }
    }

    fun setExtractArtistsFromTitle(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setExtractArtistsFromTitle(enabled)
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            syncManager.fullSync()
        }
    }
}
