package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.feed.FeedInnerTubeApi
import com.unshoo.pixelmusic.data.feed.FeedRepository
import com.unshoo.pixelmusic.data.feed.YouTubeMusicTrack
import com.unshoo.pixelmusic.data.feed.YouTubePlaylistResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedPlaylistDetailUiState {
    data object Loading : FeedPlaylistDetailUiState

    @Immutable
    data class Success(
        val playlist: YouTubePlaylistResult,
        val isSaving: Boolean = false,
        val savedToLibrary: Boolean = false,
        val saveError: String? = null,
        val isLoadingMore: Boolean = false,
        val loadError: String? = null,
    ) : FeedPlaylistDetailUiState

    data class Error(val message: String) : FeedPlaylistDetailUiState
}

@HiltViewModel
class FeedPlaylistDetailViewModel @Inject constructor(
    private val innerTube: FeedInnerTubeApi,
    private val feedRepository: FeedRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FeedPlaylistDetailUiState>(FeedPlaylistDetailUiState.Loading)
    val uiState: StateFlow<FeedPlaylistDetailUiState> = _uiState.asStateFlow()

    private var currentPlaylistId: String? = null
    private var loadJob: Job? = null

    fun load(playlistId: String, force: Boolean = false) {
        if (playlistId.isBlank()) {
            _uiState.value = FeedPlaylistDetailUiState.Error("This playlist is unavailable.")
            return
        }
        if (!force && playlistId == currentPlaylistId && _uiState.value !is FeedPlaylistDetailUiState.Error) return
        loadJob?.cancel()
        val previous = if (playlistId == currentPlaylistId) _uiState.value as? FeedPlaylistDetailUiState.Success else null
        currentPlaylistId = playlistId
        _uiState.value = previous?.copy(isLoadingMore = true, loadError = null) ?: FeedPlaylistDetailUiState.Loading
        loadJob = viewModelScope.launch {
            val liked = playlistId == "yt_liked"
            val recent = playlistId == "yt_recent"
            val title = if (liked) "Liked on YouTube" else if (recent) "Recently played" else "Playlist"
            val author = if (liked) "Your favorites" else "YouTube Music"

            fun showTracks(tracks: List<YouTubeMusicTrack>) {
                coroutineContext.ensureActive()
                if (tracks.isEmpty()) return
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                if (state != null && state.playlist.tracks.size > tracks.size) return
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    YouTubePlaylistResult(
                        id = playlistId,
                        title = title,
                        author = author,
                        artworkUrl = tracks.firstOrNull()?.artworkUrl,
                        trackCount = tracks.size,
                        tracks = tracks
                    ),
                    isLoadingMore = true,
                )
            }

            if (liked) showTracks(feedRepository.getCachedFeed()?.ytLikedSongs.orEmpty())
            if (recent) showTracks(feedRepository.getCachedFeed()?.ytRecentSongs.orEmpty())

            try {
                val result = if (recent) {
                    val taste = innerTube.fetchTasteSignals(recentLimit = 50, likedLimit = 0, feedLimit = 0)
                    taste.recentTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
                        YouTubePlaylistResult(
                            id = playlistId,
                            title = title,
                            author = author,
                            artworkUrl = tracks.firstOrNull()?.artworkUrl,
                            trackCount = tracks.size,
                            tracks = tracks
                        )
                    }
                } else {
                    innerTube.fetchPlaylist(if (liked) "LM" else playlistId, progressive = true, onPageLoaded = ::showTracks)
                } ?: throw java.io.IOException("Couldn't finish loading this playlist. Tap Retry.")

                coroutineContext.ensureActive()
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    result.copy(
                        id = playlistId,
                        title = if (liked || recent) title else result.title.ifBlank { title },
                        author = result.author?.takeIf(String::isNotBlank) ?: author
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                val message = error.message ?: "Couldn't finish loading. Tap Retry."
                _uiState.value = state?.copy(isLoadingMore = false, loadError = message)
                    ?: FeedPlaylistDetailUiState.Error(message)
            }
        }
    }

    fun retry() {
        currentPlaylistId?.let { load(it, force = true) }
    }
}
