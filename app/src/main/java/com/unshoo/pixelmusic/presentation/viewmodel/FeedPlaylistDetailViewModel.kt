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

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

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
    private val userPreferencesRepository: UserPreferencesRepository,
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
            val isNewReleases = playlistId == "new_releases"
            val title = when {
                liked -> "Liked on YouTube"
                recent -> "Recently played"
                isNewReleases -> "New Releases"
                else -> "Playlist"
            }
            val author = when {
                liked -> "Your favorites"
                recent -> "Recently played tracks"
                isNewReleases -> "From your artists"
                else -> "YouTube Music"
            }

            fun showTracks(tracks: List<YouTubeMusicTrack>, customArt: String? = null) {
                coroutineContext.ensureActive()
                if (tracks.isEmpty()) return
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                if (state != null && state.playlist.tracks.size > tracks.size) return
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    YouTubePlaylistResult(
                        id = playlistId,
                        title = title,
                        author = author,
                        artworkUrl = customArt ?: tracks.firstOrNull()?.artworkUrl,
                        trackCount = tracks.size,
                        tracks = tracks
                    ),
                    isLoadingMore = true,
                )
            }

            if (liked) showTracks(feedRepository.getCachedFeed()?.ytLikedSongs.orEmpty())
            if (recent) showTracks(feedRepository.getCachedFeed()?.ytRecentSongs.orEmpty())

            try {
                val pureYtMusic = runCatching { userPreferencesRepository.pureYtMusicOnlyFlow.first() }.getOrDefault(false)

                val result = when {
                    recent -> {
                        val taste = innerTube.fetchTasteSignals(recentLimit = 50, likedLimit = 0, feedLimit = 0)
                        taste.recentTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
                            val filtered = if (pureYtMusic) tracks.filterNot { it.isVideo } else tracks
                            YouTubePlaylistResult(
                                id = playlistId,
                                title = title,
                                author = author,
                                artworkUrl = filtered.firstOrNull()?.artworkUrl,
                                trackCount = filtered.size,
                                tracks = filtered
                            )
                        }
                    }
                    isNewReleases -> {
                        val feed = feedRepository.getCachedFeed() ?: feedRepository.loadFeed()
                        var releases = feed.newReleases
                        if (releases.isEmpty()) {
                            val releaseYear = java.time.Year.now().value.toString()
                            val top = feed.topArtists.filter { !it.browseId.isNullOrBlank() }.take(6)
                            releases = top.mapNotNull { artist ->
                                val page = artist.browseId?.let { id ->
                                    runCatching { innerTube.fetchArtistPage(id, artist.name) }.getOrNull()
                                }
                                (page?.albums.orEmpty() + page?.singles.orEmpty())
                                    .filter { (it.year == releaseYear || it.year == null) && it.browseId.isNotBlank() }
                                    .take(2)
                                    .map { r ->
                                        com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary(
                                            id = r.browseId,
                                            title = r.title,
                                            author = artist.name,
                                            artworkUrl = r.artworkUrl
                                        )
                                    }
                            }.flatten().distinctBy { it.id }
                        }

                        if (releases.isEmpty()) {
                            throw java.io.IOException("No new releases from your artists found.")
                        }

                        val coverArt = releases.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                        val accumulatedTracks = mutableListOf<YouTubeMusicTrack>()

                        for (release in releases) {
                            coroutineContext.ensureActive()
                            val songs = runCatching {
                                innerTube.fetchAlbumPage(release.id)?.songs?.takeIf { it.isNotEmpty() }
                                    ?: innerTube.fetchPlaylist(release.id)?.tracks?.takeIf { it.isNotEmpty() }
                            }.getOrNull().orEmpty().filter { !pureYtMusic || !it.isVideo }

                            if (songs.isNotEmpty()) {
                                accumulatedTracks.addAll(songs)
                                showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                            }
                        }

                        val distinctTracks = accumulatedTracks.distinctBy { it.videoId }
                        if (distinctTracks.isEmpty()) {
                            throw java.io.IOException("No new release tracks found.")
                        }

                        YouTubePlaylistResult(
                            id = playlistId,
                            title = title,
                            author = author,
                            artworkUrl = coverArt ?: distinctTracks.firstOrNull()?.artworkUrl,
                            trackCount = distinctTracks.size,
                            tracks = distinctTracks
                        )
                    }
                    else -> {
                        val pl = innerTube.fetchPlaylist(if (liked) "LM" else playlistId, progressive = true, onPageLoaded = ::showTracks)
                        if (pl != null) {
                            pl
                        } else {
                            // Fallback to fetchAlbumPage for album/single browse IDs
                            innerTube.fetchAlbumPage(playlistId)?.let { album ->
                                val songs = album.songs.filter { !pureYtMusic || !it.isVideo }
                                YouTubePlaylistResult(
                                    id = playlistId,
                                    title = album.title,
                                    author = album.artist,
                                    artworkUrl = album.artworkUrl,
                                    trackCount = songs.size,
                                    tracks = songs
                                )
                            }
                        }
                    }
                } ?: throw java.io.IOException("Couldn't finish loading this playlist. Tap Retry.")

                coroutineContext.ensureActive()
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    result.copy(
                        id = playlistId,
                        title = if (liked || recent || isNewReleases) title else result.title.ifBlank { title },
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
