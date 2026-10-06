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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.utils.ContentFilterUtils
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

            val pureYtMusic = runCatching { userPreferencesRepository.pureYtMusicOnlyFlow.first() }.getOrDefault(false)
            val filterCoverAndLofi = runCatching { userPreferencesRepository.filterCoverAndLofiFlow.first() }.getOrDefault(true)
            val filterKeywords = runCatching { userPreferencesRepository.filterKeywordsFlow.first() }.getOrDefault(ContentFilterUtils.DEFAULT_FILTER_KEYWORDS)
            fun filterTrack(t: YouTubeMusicTrack): Boolean = (!pureYtMusic || !t.isVideo) && (!filterCoverAndLofi || !ContentFilterUtils.isCoverOrLofi(t, filterKeywords))

            fun showTracks(tracks: List<YouTubeMusicTrack>, customArt: String? = null) {
                coroutineContext.ensureActive()
                val filtered = tracks.filter(::filterTrack)
                if (filtered.isEmpty()) return
                val state = _uiState.value as? FeedPlaylistDetailUiState.Success
                if (state != null && state.playlist.tracks.size > filtered.size) return
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    YouTubePlaylistResult(
                        id = playlistId,
                        title = title,
                        author = author,
                        artworkUrl = customArt ?: filtered.firstOrNull()?.artworkUrl,
                        trackCount = filtered.size,
                        tracks = filtered
                    ),
                    isLoadingMore = true,
                )
            }

            if (liked) showTracks(feedRepository.getCachedFeed()?.ytLikedSongs.orEmpty())
            if (recent) showTracks(feedRepository.getCachedFeed()?.ytRecentSongs.orEmpty())

            try {
                val result = when {
                    recent -> {
                        val taste = innerTube.fetchTasteSignals(recentLimit = 50, likedLimit = 0, feedLimit = 0)
                        taste.recentTracks.takeIf { it.isNotEmpty() }?.let { tracks ->
                            val filtered = tracks.filter(::filterTrack)
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
                        val accumulatedTracks = mutableListOf<YouTubeMusicTrack>()
                        var coverArt: String? = null

                        // 1. Check if FeedRepository already has cached/loaded user new releases
                        val cachedReleases = feedRepository.getCachedFeed()?.newReleases.orEmpty()
                            .filter { !it.id.startsWith("VL") && !it.id.startsWith("PL") && !it.id.startsWith("RD") }

                        val directReleases = cachedReleases.ifEmpty {
                            // Fetch user's home sections
                            val home = withContext(Dispatchers.IO) {
                                runCatching { unshoo.ianshulyadav.pixelmusic.innertube.YouTube.home().getOrNull() }.getOrNull()
                            }
                            var homeSections = home?.sections.orEmpty()
                            if (homeSections.none { s -> s.title.contains("release", ignoreCase = true) || s.title.contains("released", ignoreCase = true) } && home?.continuation != null) {
                                val cont = withContext(Dispatchers.IO) {
                                    runCatching { unshoo.ianshulyadav.pixelmusic.innertube.YouTube.home(continuation = home.continuation).getOrNull() }.getOrNull()
                                }
                                if (cont != null && cont.sections.isNotEmpty()) {
                                    homeSections = homeSections + cont.sections
                                }
                            }

                            val newReleaseSections = homeSections.filter { section ->
                                val t = section.title.lowercase()
                                !t.contains("video") && !t.contains("videos") && (
                                    t.contains("released") ||
                                    t.contains("new release") ||
                                    t.contains("new album") ||
                                    t.contains("latest release") ||
                                    t.contains("new music") ||
                                    t.contains("recent release") ||
                                    t.contains("novedades") ||
                                    t.contains("nouveautés") ||
                                    t.contains("veröffentlichungen") ||
                                    t.contains("release radar") ||
                                    t.contains("new for you") ||
                                    t.contains("fresh drops") ||
                                    section.label?.lowercase()?.contains("release") == true
                                )
                            }

                            val items = newReleaseSections.flatMap { it.items }
                            items.mapNotNull { item ->
                                when (item) {
                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem -> {
                                        com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary(
                                            id = item.browseId,
                                            title = item.title,
                                            author = item.artists?.firstOrNull()?.name ?: "Album",
                                            artworkUrl = item.thumbnail
                                        )
                                    }
                                    is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem -> {
                                        com.unshoo.pixelmusic.data.feed.YouTubePlaylistSummary(
                                            id = item.id,
                                            title = item.title,
                                            author = item.artists.firstOrNull()?.name ?: "Single",
                                            artworkUrl = item.thumbnail
                                        )
                                    }
                                    // Strictly ignore PlaylistItem (editorial/curated compilation playlists)
                                    else -> null
                                }
                            }.distinctBy { it.id }
                        }

                        if (directReleases.isNotEmpty()) {
                            coverArt = directReleases.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl

                            // First, immediately add single track releases
                            val directSongs = directReleases.filter { !it.id.startsWith("MPRE") && !it.id.startsWith("FEmusic_album") }
                                .map { r ->
                                    YouTubeMusicTrack(
                                        videoId = r.id,
                                        title = r.title,
                                        artist = r.author ?: "Unknown artist",
                                        album = r.title,
                                        artworkUrl = r.artworkUrl
                                    )
                                }.filter(::filterTrack)

                            if (directSongs.isNotEmpty()) {
                                accumulatedTracks.addAll(directSongs)
                                showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                            }

                            // Second, concurrently fetch tracks from user albums
                            val albumReleases = directReleases.filter { it.id.startsWith("MPRE") || it.id.startsWith("FEmusic_album") }
                            kotlinx.coroutines.coroutineScope {
                                val albumJobs = albumReleases.map { rel ->
                                    async(Dispatchers.IO) {
                                        val page = runCatching { innerTube.fetchAlbumPage(rel.id) }.getOrNull()
                                        val fallbackArtist = rel.author ?: page?.artist ?: "Artist"
                                        val fallbackThumb = rel.artworkUrl?.takeIf { it.isNotBlank() } ?: page?.artworkUrl
                                        page?.songs?.map { s ->
                                            s.copy(
                                                artist = if (s.artist.isBlank() || s.artist == "Unknown artist") fallbackArtist else s.artist,
                                                album = s.album ?: rel.title,
                                                artworkUrl = s.artworkUrl ?: fallbackThumb
                                            )
                                        }.orEmpty().filter(::filterTrack)
                                    }
                                }

                                for (job in albumJobs) {
                                    coroutineContext.ensureActive()
                                    val songs = job.await()
                                    if (songs.isNotEmpty()) {
                                        accumulatedTracks.addAll(songs)
                                        showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                                    }
                                }
                            }
                        }

                        // Fallback to personalized feed repository releases if home section was empty
                        if (accumulatedTracks.isEmpty()) {
                            val feed = feedRepository.getCachedFeed() ?: feedRepository.loadFeed()
                            var releases = feed.newReleases
                                .filter { !it.id.startsWith("VL") && !it.id.startsWith("PL") && !it.id.startsWith("RD") }
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

                            coverArt = releases.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl

                            for (release in releases) {
                                coroutineContext.ensureActive()
                                val songs = if (release.id.startsWith("MPRE") || release.id.startsWith("FEmusic_album")) {
                                    val page = runCatching { innerTube.fetchAlbumPage(release.id) }.getOrNull()
                                    page?.songs?.map { s ->
                                        s.copy(
                                            artist = (if (s.artist.isBlank() || s.artist == "Unknown artist") release.author else s.artist) ?: "Unknown artist",
                                            album = s.album ?: release.title,
                                            artworkUrl = s.artworkUrl ?: release.artworkUrl
                                        )
                                    }.orEmpty()
                                } else if (release.id.startsWith("VL") || release.id.startsWith("PL")) {
                                    runCatching { innerTube.fetchPlaylist(release.id)?.tracks }.getOrNull().orEmpty()
                                } else {
                                    listOf(
                                        YouTubeMusicTrack(
                                            videoId = release.id,
                                            title = release.title,
                                            artist = release.author ?: "Unknown artist",
                                            artworkUrl = release.artworkUrl
                                        )
                                    )
                                }.filter(::filterTrack)

                                if (songs.isNotEmpty()) {
                                    accumulatedTracks.addAll(songs)
                                    showTracks(accumulatedTracks.distinctBy { it.videoId }, customArt = coverArt)
                                }
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
                            val filteredTracks = pl.tracks.filter(::filterTrack)
                            pl.copy(tracks = filteredTracks, trackCount = filteredTracks.size)
                        } else {
                            // Fallback to fetchAlbumPage for album/single browse IDs
                            innerTube.fetchAlbumPage(playlistId)?.let { album ->
                                val songs = album.songs.filter(::filterTrack)
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
                val finalTracks = result.tracks.filter(::filterTrack)
                _uiState.value = FeedPlaylistDetailUiState.Success(
                    result.copy(
                        id = playlistId,
                        title = if (liked || recent || isNewReleases) title else result.title.ifBlank { title },
                        author = result.author?.takeIf(String::isNotBlank) ?: author,
                        tracks = finalTracks,
                        trackCount = finalTracks.size
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
