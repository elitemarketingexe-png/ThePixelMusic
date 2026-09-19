package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.ArtistImageRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube as InnerTubeYouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ArtistPage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.SearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Holds the full UI state for ArtistDetailScreen.
 *
 * [effectiveImageUrl] is the resolved image to display (custom takes priority over Deezer).
 * It is updated after artist data loads and again whenever the user changes the custom image.
 */
data class ArtistDetailUiState(
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val popularSongs: List<Song> = emptyList(),
    val albumSections: List<ArtistAlbumSection> = emptyList(),
    val singlesAndEPs: List<ArtistAlbumSection> = emptyList(),
    val localAlbumSections: List<ArtistAlbumSection> = emptyList(),
    val effectiveImageUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOnlineArtist: Boolean = false,
    val artistDescription: String? = null,
    val subscriberCount: String? = null,
    val browseId: String? = null,
    val albumsMoreEndpoint: BrowseEndpoint? = null,
    val singlesMoreEndpoint: BrowseEndpoint? = null,
    val songsMoreEndpoint: BrowseEndpoint? = null,
    val allItems: List<ArtistAlbumSection> = emptyList(),
    val isAllItemsLoading: Boolean = false,
    val allItemsContinuation: String? = null,
    val allItemsError: String? = null,
    val popularSongsAll: List<Song> = emptyList(),
    val isPopularSongsAllLoading: Boolean = false,
    val popularSongsAllContinuation: String? = null,
    val popularSongsAllError: String? = null
)

enum class ArtistSectionType { ALBUM, SINGLE_EP, SONGS }

@Immutable
data class ArtistAlbumSection(
    val albumId: Long,
    val title: String,
    val year: Int?,
    val albumArtUriString: String?,
    val songs: List<Song>,
    val browseId: String? = null,
    val sectionType: ArtistSectionType = ArtistSectionType.ALBUM
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
    val themeStateHolder: ThemeStateHolder,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
    private val musicDao: com.unshoo.pixelmusic.data.database.MusicDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private val _artistColorScheme = MutableStateFlow<ColorSchemePair?>(null)
    val artistColorScheme: StateFlow<ColorSchemePair?> = _artistColorScheme.asStateFlow()

    val isSubscribed: Flow<Boolean> = combine(
        savedStateHandle.getStateFlow<String?>("artistId", null),
        userPreferencesRepository.subscribedArtistIdsFlow
    ) { artistIdStr, subscribedIds ->
        artistIdStr != null && subscribedIds.contains(artistIdStr)
    }

    fun toggleSubscription() {
        val artistIdStr = savedStateHandle.get<String?>("artistId") ?: return
        viewModelScope.launch {
            val browseId = uiState.value.browseId ?: if (artistIdStr.toLongOrNull() == null) artistIdStr else null
            val currentSubscribed = userPreferencesRepository.subscribedArtistIdsFlow.first()
            val isCurrentlySubscribed = currentSubscribed.contains(artistIdStr)
            val subscribe = !isCurrentlySubscribed

            if (browseId != null) {
                try {
                    withContext(Dispatchers.IO) {
                        InnerTubeYouTube.subscribeChannel(browseId, subscribe)
                    }
                } catch (e: Exception) {
                    Log.e("ArtistDetailViewModel", "Failed to toggle remote subscription", e)
                }
            }

            userPreferencesRepository.subscribeArtist(artistIdStr, subscribe)
            if (browseId != null && browseId != artistIdStr) {
                userPreferencesRepository.subscribeArtist(browseId, subscribe)
            }

            if (subscribe) {
                val artist = uiState.value.artist
                if (artist != null) {
                    val effectiveChannelId = browseId ?: artist.channelId
                    val finalArtistId = if (artist.id < 0) {
                        artist.id
                    } else {
                        YouTubeIdUtils.toUnifiedArtistId(artist.name)
                    }
                    val artistEntity = com.unshoo.pixelmusic.data.database.ArtistEntity(
                        id = finalArtistId,
                        name = artist.name,
                        trackCount = artist.songCount,
                        imageUrl = artist.imageUrl ?: uiState.value.effectiveImageUrl,
                        customImageUri = artist.customImageUri,
                        channelId = effectiveChannelId
                    )
                    withContext(Dispatchers.IO) {
                        musicDao.insertArtists(listOf(artistEntity))
                    }
                }
            }
        }
    }

    init {
        savedStateHandle.getStateFlow<String?>("artistId", null)
            .onEach { idString ->
                if (idString != null) {
                    loadArtistData(idString)
                } else {
                    _uiState.update { it.copy(error = context.getString(R.string.artist_id_not_found), isLoading = false) }
                }
            }
            .launchIn(viewModelScope)
    }

    private var currentLoadJob: Job? = null

    private fun titlesMatch(title1: String?, title2: String?): Boolean {
        if (title1.isNullOrBlank() || title2.isNullOrBlank()) return false
        fun normalize(t: String): String = t.lowercase()
            .replace(Regex("""(?i)\(.*?\)|\[.*?\]"""), "") // remove (Official Video), [Remastered], etc.
            .replace(Regex("""(?i)\s*-\s*(single|ep|audio|video|lyrics?|remastered|bonus track|acoustic|live).*"""), "")
            .replace(Regex("""[^a-zA-Z0-9\p{L}\s]"""), " ") // keep letters and digits across scripts
            .trim()
            .replace(Regex("""\s+"""), " ")
        val n1 = normalize(title1)
        val n2 = normalize(title2)
        if (n1.isBlank() || n2.isBlank()) return false
        if (n1 == n2) return true

        val words1 = n1.split(" ").filter { it.isNotBlank() }
        val words2 = n2.split(" ").filter { it.isNotBlank() }
        val (shorterWords, longerWords) = if (words1.size <= words2.size) words1 to words2 else words2 to words1
        if (shorterWords.size >= 2 || (shorterWords.size == 1 && shorterWords[0].length >= 5)) {
            val longerWordSet = longerWords.toSet()
            if (shorterWords.all { it in longerWordSet }) return true
        }
        return false
    }

    private suspend fun tryConfirmOnlineArtist(
        artistName: String,
        localSongs: List<Song>
    ): String? {
        val cleanName = artistName.replace(Regex("""(?i)\s*-\s*topic$"""), "").trim()
        if (cleanName.isBlank()) return null

        // 1. Check if user already has online songs for this artist in the library
        val onlineSongsInLibrary: List<Song> = withContext(Dispatchers.IO) {
            musicDao.getOnlineSongsByArtistName(cleanName).map { it.toSong() }
        }

        // If the artist which is getting replaced has NO online song in this library:
        // Blind YouTube name searching will never overwrite them with a YouTube artist.
        if (onlineSongsInLibrary.isEmpty()) {
            Log.d("ArtistDebug", "No online songs in library for '$cleanName' — strictly keeping local artist")
            return null
        }

        // 2. The library DOES have online song(s) for this artist name.
        // Now verify whether the online artist matches the local artist via song matching!

        // Direct matching: check if any local song title matches any online song in the library
        val hasDirectSongMatch = localSongs.any { localSong ->
            onlineSongsInLibrary.any { onlineSong ->
                titlesMatch(localSong.title, onlineSong.title)
            }
        }

        // Extract known channel IDs from the online songs in library
        val candidateChannelIds = onlineSongsInLibrary.flatMap { it.artists }
            .mapNotNull { it.channelId }
            .filter { it.startsWith("UC") || it.startsWith("LA") || it.startsWith("FEmusic_") }
            .distinct()

        if (hasDirectSongMatch) {
            val verifiedChannelId = candidateChannelIds.firstOrNull()
            if (verifiedChannelId != null) {
                Log.d("ArtistDebug", "Confirmed online artist for '$cleanName' via direct library song match: $verifiedChannelId")
                return verifiedChannelId
            }
        }

        // If direct match didn't give a channel ID, or if direct match didn't find identical titles
        // (e.g. user has Song A locally and Song B in online library by the same artist),
        // verify against the candidate channel's full page (songs + albums)
        val channelIdsToTest = if (candidateChannelIds.isNotEmpty()) {
            candidateChannelIds
        } else {
            // If online songs didn't have channelId embedded, search YouTube for the channel ID
            val primaryArtistName = cleanName.split(
                ", ", " & ", " feat.", " feat ", " Feat.", " Feat ", " FT.", " FT ", " ft.", " ft "
            ).firstOrNull()?.trim() ?: cleanName

            val searchResult = withContext(Dispatchers.IO) {
                InnerTubeYouTube.search(primaryArtistName, InnerTubeYouTube.SearchFilter.FILTER_ARTIST).getOrNull()
            }
            val matchingArtist = searchResult?.items?.filterIsInstance<ArtistItem>()?.find {
                it.title.trim().equals(primaryArtistName, ignoreCase = true)
            }
            listOfNotNull(matchingArtist?.id)
        }

        for (candidateBrowseId in channelIdsToTest) {
            val artistPageResult = withContext(Dispatchers.IO) {
                InnerTubeYouTube.artist(candidateBrowseId)
            }
            val artistPage = artistPageResult.getOrNull() ?: continue

            val onlineSongTitles = artistPage.sections.flatMap { section ->
                section.items.mapNotNull { (it as? SongItem)?.title }
            }
            val onlineAlbumTitles = artistPage.sections.flatMap { section ->
                section.items.mapNotNull { (it as? AlbumItem)?.title }
            }

            // Verify: does this online channel contain any of the local songs or albums?
            val hasMatchingSongOrAlbum = localSongs.any { localSong ->
                onlineSongTitles.any { ytTitle -> titlesMatch(localSong.title, ytTitle) } ||
                onlineAlbumTitles.any { ytAlbum -> titlesMatch(localSong.album, ytAlbum) }
            }

            if (hasMatchingSongOrAlbum) {
                Log.d("ArtistDebug", "Confirmed online artist $candidateBrowseId for '$cleanName' via channel page song/album match")
                return candidateBrowseId
            }
        }

        Log.d("ArtistDebug", "Online artist verification failed for '$cleanName': no matching songs found between local and online")
        return null
    }

    private fun loadArtistData(artistIdStr: String) {
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            Log.d("ArtistDebug", "loadArtistData: idStr=$artistIdStr")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val numericId = artistIdStr.toLongOrNull()
                val isExplicitBrowseId = artistIdStr.startsWith("UC") || 
                                         artistIdStr.startsWith("LA") || 
                                         artistIdStr.startsWith("FEmusic_")

                if (isExplicitBrowseId) {
                    loadOnlineArtist(artistIdStr)
                    return@launch
                }

                if (numericId != null) {
                    if (numericId > 0) {
                        // Local MediaStore artist:
                        val localSongs = musicRepository.getSongsForArtist(numericId).first()
                        val dbArtist = musicRepository.getArtistById(numericId).first()
                        val artistName = dbArtist?.name?.trim() ?: ""

                        // Check if this local artist can be confirmed from online (e.g. matching online songs in library or matching track titles on YouTube)
                        val confirmedBrowseId = tryConfirmOnlineArtist(artistName, localSongs)
                        if (confirmedBrowseId != null) {
                            loadOnlineArtist(confirmedBrowseId, localSongs = localSongs)
                            return@launch
                        }

                        // Not confirmed online: strictly load local artist with local songs
                        loadLocalArtist(numericId)
                        return@launch
                    } else {
                        // Negative ID: could be YouTube sync, Telegram, Cloud
                        val dbArtist = musicRepository.getArtistById(numericId).first()
                        val localSongs = musicRepository.getSongsForArtist(numericId).first()

                        if (dbArtist != null && !dbArtist.channelId.isNullOrBlank() && 
                            (dbArtist.channelId.startsWith("UC") || dbArtist.channelId.startsWith("LA") || dbArtist.channelId.startsWith("FEmusic_"))) {
                            loadOnlineArtist(dbArtist.channelId, localSongs = localSongs)
                            return@launch
                        }

                        val artistName = dbArtist?.name ?: ""
                        val confirmedBrowseId = tryConfirmOnlineArtist(artistName, localSongs)
                        if (confirmedBrowseId != null) {
                            loadOnlineArtist(confirmedBrowseId, localSongs = localSongs)
                            return@launch
                        }

                        loadLocalArtist(numericId)
                        return@launch
                    }
                } else {
                    // artistIdStr is a raw string name (e.g. "Bharat Chauhan")
                    val cleanName = artistIdStr.replace(Regex("""(?i)\s*-\s*topic$"""), "").trim()
                    val localId = musicDao.getArtistIdByNormalizedName(cleanName)
                    val localSongs = if (localId != null) musicRepository.getSongsForArtist(localId).first() else emptyList()

                    val confirmedBrowseId = tryConfirmOnlineArtist(cleanName, localSongs)
                    if (confirmedBrowseId != null) {
                        loadOnlineArtist(confirmedBrowseId, localSongs = localSongs)
                        return@launch
                    }

                    if (localId != null) {
                        loadLocalArtist(localId)
                        return@launch
                    }

                    _uiState.update {
                        it.copy(
                            error = context.getString(R.string.could_not_find_artist),
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun loadOnlineArtist(browseId: String, localSongs: List<Song> = emptyList()) {
        val artistPageResult = withContext(Dispatchers.IO) {
            InnerTubeYouTube.artist(browseId)
        }

        artistPageResult.onSuccess { artistPage ->
            withContext(Dispatchers.Default) {
                val artistItem = artistPage.artist

                // ── Popular Songs: extract from the "Songs" section ──
                val ytSongsSection = artistPage.sections.find {
                    it.title.contains("Songs", ignoreCase = true) ||
                    it.title.contains("Popular", ignoreCase = true)
                }
                val popularSongs = ytSongsSection?.items?.mapNotNull { item ->
                    (item as? SongItem)?.toNativeSong()
                }?.take(10) ?: emptyList()

                // If localSongs was not explicitly passed, check if the user has local songs in library
                // that match this online artist's songs or albums
                val verifiedLocalSongs = if (localSongs.isNotEmpty()) {
                    localSongs
                } else {
                    val localCandidates: List<Song> = withContext(Dispatchers.IO) {
                        musicDao.getLocalSongsByArtistName(artistItem.title).map { it.toSong() }
                    }
                    if (localCandidates.isNotEmpty()) {
                        val onlineSongTitles = artistPage.sections.flatMap { section ->
                            section.items.mapNotNull { (it as? SongItem)?.title }
                        }
                        val onlineAlbumTitles = artistPage.sections.flatMap { section ->
                            section.items.mapNotNull { (it as? AlbumItem)?.title }
                        }
                        val matches = localCandidates.any { localSong ->
                            onlineSongTitles.any { ytTitle -> titlesMatch(localSong.title, ytTitle) } ||
                            onlineAlbumTitles.any { ytAlbum -> titlesMatch(localSong.album, ytAlbum) }
                        }
                        if (matches) localCandidates else emptyList()
                    } else {
                        emptyList()
                    }
                }

                val artistModel = Artist(
                    id = YouTubeIdUtils.toUnifiedArtistId(artistItem.title),
                    name = artistItem.title,
                    songCount = if (verifiedLocalSongs.isNotEmpty()) verifiedLocalSongs.size else popularSongs.size,
                    imageUrl = artistItem.thumbnail,
                    channelId = browseId
                )

                // ── Albums: sections titled "Albums" or "Releases" ──
                fun AlbumItem.toAlbumSection(): ArtistAlbumSection {
                    return ArtistAlbumSection(
                        albumId = this.browseId.hashCode().toLong(),
                        title = this.title,
                        year = this.year,
                        albumArtUriString = this.thumbnail,
                        browseId = this.browseId,
                        songs = emptyList(),
                        sectionType = ArtistSectionType.ALBUM
                    )
                }

                val albumsSection = artistPage.sections.firstOrNull { section ->
                    section.title.contains("Albums", ignoreCase = true) ||
                    section.title.contains("Releases", ignoreCase = true)
                }
                val albumSections = albumsSection?.items?.mapNotNull { item ->
                    when (item) {
                        is AlbumItem -> item.toAlbumSection()
                        else -> null
                    }
                }.orEmpty()

                // ── Singles & EPs: sections titled "Singles", "EP", or "EPs" ──
                val singlesSection = artistPage.sections.firstOrNull { section ->
                    section.title.contains("Single", ignoreCase = true) ||
                    section.title.contains("EP", ignoreCase = true)
                }
                val singlesAndEPs = singlesSection?.items?.mapNotNull { item ->
                    when (item) {
                        is AlbumItem -> ArtistAlbumSection(
                            albumId = item.browseId.hashCode().toLong(),
                            title = item.title,
                            year = item.year,
                            albumArtUriString = item.thumbnail,
                            browseId = item.browseId,
                            songs = emptyList(),
                            sectionType = ArtistSectionType.SINGLE_EP
                        )
                        else -> null
                    }
                }.orEmpty()

                // ── Local album sections if user has matching local songs ──
                val localAlbumSections = if (verifiedLocalSongs.isNotEmpty()) {
                    buildAlbumSections(verifiedLocalSongs)
                } else emptyList()

                val effectiveImageUrl = artistItem.thumbnail
                val newScheme = if (!effectiveImageUrl.isNullOrBlank()) {
                    try {
                        themeStateHolder.getOrGenerateColorScheme(effectiveImageUrl)
                    } catch (e: Exception) {
                        null
                    }
                } else null

                withContext(Dispatchers.Main) {
                    _artistColorScheme.value = newScheme
                    _uiState.value = ArtistDetailUiState(
                        artist = artistModel,
                        songs = if (popularSongs.isNotEmpty()) popularSongs else verifiedLocalSongs,
                        popularSongs = popularSongs,
                        albumSections = albumSections,
                        singlesAndEPs = singlesAndEPs,
                        localAlbumSections = localAlbumSections,
                        effectiveImageUrl = effectiveImageUrl,
                        isLoading = false,
                        isOnlineArtist = true,
                        artistDescription = artistPage.description,
                        subscriberCount = artistItem.subscriberCountText,
                        browseId = browseId,
                        albumsMoreEndpoint = albumsSection?.moreEndpoint,
                        singlesMoreEndpoint = singlesSection?.moreEndpoint,
                        songsMoreEndpoint = ytSongsSection?.moreEndpoint
                    )
                }
            }
        }.onFailure { e ->
            if (localSongs.isNotEmpty() && localSongs.firstOrNull()?.artistId != null) {
                loadLocalArtist(localSongs.first().artistId)
            } else {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun loadLocalArtist(id: Long) {
        val artistDetailsFlow = musicRepository.getArtistById(id)
        val artistSongsFlow = musicRepository.getSongsForArtist(id)

        combine(artistDetailsFlow, artistSongsFlow) { artist, songs ->
            Log.d("ArtistDebug", "loadArtistData: id=$id found=${artist != null} songs=${songs.size}")
            artist to songs
        }
            .catch { e ->
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
            .collect { (artist, songs) ->
                withContext(Dispatchers.Default) {
                    if (artist == null) {
                        _uiState.update {
                            it.copy(error = context.getString(R.string.could_not_find_artist), isLoading = false)
                        }
                        return@withContext
                    }

                    val albumSections = buildAlbumSections(songs)
                    val orderedSongs = albumSections.flatMap { it.songs }

                    val effectiveUrl = try {
                        artistImageRepository.getEffectiveArtistImageUrl(
                            artistId = artist.id,
                            artistName = artist.name
                        )
                    } catch (e: Exception) {
                        Log.w("ArtistDebug", "Failed to resolve effective artist image: ${e.message}")
                        artist.effectiveImageUrl
                    }

                    val newScheme = if (!effectiveUrl.isNullOrBlank()) {
                        try {
                            themeStateHolder.getOrGenerateColorScheme(effectiveUrl)
                        } catch (e: Exception) {
                            Log.w("ArtistDebug", "Color scheme pre-warm failed: ${e.message}")
                            null
                        }
                    } else null

                    withContext(Dispatchers.Main) {
                        _artistColorScheme.value = newScheme
                        _uiState.value = ArtistDetailUiState(
                            artist = artist.copy(
                                imageUrl = if (artist.customImageUri.isNullOrBlank()) effectiveUrl else artist.imageUrl
                            ),
                            songs = orderedSongs,
                            popularSongs = emptyList(),
                            albumSections = albumSections,
                            singlesAndEPs = emptyList(),
                            effectiveImageUrl = effectiveUrl,
                            isLoading = false,
                            isOnlineArtist = false
                        )
                    }
                }
            }
    }

    /**
     * Called from the UI when the user selects a custom image from the system photo picker.
     * Copies the image to internal storage, persists the path to DB, and triggers palette regeneration.
     */
    fun setCustomImage(sourceUri: Uri) {
        val artistId = _uiState.value.artist?.id ?: return
        viewModelScope.launch {
            try {
                val internalPath = artistImageRepository.setCustomArtistImage(context, artistId, sourceUri)
                if (!internalPath.isNullOrBlank()) {
                    val oldEffectiveUrl = _uiState.value.effectiveImageUrl

                    // Regenerate palette from the new image url — invalidate old and warm-up new
                    if (!oldEffectiveUrl.isNullOrBlank() && oldEffectiveUrl != internalPath) {
                        themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                    }
                    val newScheme = try {
                        themeStateHolder.forceRegenerateColorScheme(internalPath)
                        themeStateHolder.getOrGenerateColorScheme(internalPath)
                    } catch (e: Exception) {
                        Log.w("ArtistDebug", "Failed to regenerate color scheme for custom image: ${e.message}")
                        null
                    }

                    _artistColorScheme.value = newScheme
                    _uiState.update { state ->
                        // Cache-busting: add timestamp to internalPath to force Coil to reload
                        val effectiveUrlWithBust = "$internalPath?t=${System.currentTimeMillis()}"
                        state.copy(
                            effectiveImageUrl = effectiveUrlWithBust,
                            artist = state.artist?.copy(customImageUri = internalPath)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistDebug", "Failed to set custom image: ${e.message}")
            }
        }
    }

    /**
     * Called when the user wants to revert to the Deezer-sourced image.
     */
    fun clearCustomImage() {
        val artist = _uiState.value.artist ?: return
        viewModelScope.launch {
            try {
                val oldEffectiveUrl = _uiState.value.effectiveImageUrl
                artistImageRepository.clearCustomArtistImage(context, artist.id)

                // Fall back to Deezer URL
                val deezerUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                val newEffectiveUrl = deezerUrl.takeIf { !it.isNullOrBlank() }

                // Invalidate old custom image palette
                if (!oldEffectiveUrl.isNullOrBlank()) {
                    themeStateHolder.forceRegenerateColorScheme(oldEffectiveUrl)
                }

                val newScheme = if (!newEffectiveUrl.isNullOrBlank()) {
                    try {
                        themeStateHolder.getOrGenerateColorScheme(newEffectiveUrl)
                    } catch (e: Exception) {
                        Log.w("ArtistDebug", "Failed to regenerate palette after clear: ${e.message}")
                        null
                    }
                } else null

                _artistColorScheme.value = newScheme
                _uiState.update { state ->
                    state.copy(
                        effectiveImageUrl = newEffectiveUrl,
                        artist = state.artist?.copy(customImageUri = null, imageUrl = deezerUrl)
                    )
                }

            } catch (e: Exception) {
                Log.e("ArtistDebug", "Failed to clear custom image: ${e.message}")
            }
        }
    }

    fun removeSongFromAlbumSection(songId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.albumSections.map { section ->
                val updatedSongs = section.songs.filterNot { it.id == songId }
                section.copy(songs = updatedSongs)
            }.filter { it.songs.isNotEmpty() }

            currentState.copy(
                albumSections = updatedAlbumSections,
                popularSongs = currentState.popularSongs.filterNot { it.id == songId },
                songs = currentState.songs.filterNot { it.id == songId }
            )
        }
    }

    private var allItemsJob: Job? = null

    fun loadAllItems(type: String) {
        val endpoint = if (type == "singles") {
            _uiState.value.singlesMoreEndpoint
        } else {
            _uiState.value.albumsMoreEndpoint
        }

        if (endpoint == null) {
            val initialItems = if (type == "singles") {
                _uiState.value.singlesAndEPs
            } else {
                _uiState.value.albumSections
            }
            _uiState.update { it.copy(allItems = initialItems, allItemsContinuation = null, isAllItemsLoading = false) }
            return
        }

        allItemsJob?.cancel()
        allItemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isAllItemsLoading = true, allItems = emptyList(), allItemsContinuation = null, allItemsError = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.artistItems(endpoint)
                }
                result.onSuccess { page ->
                    val mappedItems = page.items.mapNotNull { item ->
                        when (item) {
                            is AlbumItem -> ArtistAlbumSection(
                                albumId = item.browseId.hashCode().toLong(),
                                title = item.title,
                                year = item.year,
                                albumArtUriString = item.thumbnail,
                                browseId = item.browseId,
                                songs = emptyList(),
                                sectionType = if (type == "singles") ArtistSectionType.SINGLE_EP else ArtistSectionType.ALBUM
                            )
                            else -> null
                        }
                    }
                    _uiState.update {
                        it.copy(
                            allItems = mappedItems,
                            allItemsContinuation = page.continuation,
                            isAllItemsLoading = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            allItemsError = e.localizedMessage,
                            isAllItemsLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        allItemsError = e.localizedMessage,
                        isAllItemsLoading = false
                    )
                }
            }
        }
    }

    fun loadMoreAllItems(type: String) {
        val continuation = _uiState.value.allItemsContinuation ?: return
        if (_uiState.value.isAllItemsLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAllItemsLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.artistItemsContinuation(continuation)
                }
                result.onSuccess { page ->
                    val mappedItems = page.items.mapNotNull { item ->
                        when (item) {
                            is AlbumItem -> ArtistAlbumSection(
                                albumId = item.browseId.hashCode().toLong(),
                                title = item.title,
                                year = item.year,
                                albumArtUriString = item.thumbnail,
                                browseId = item.browseId,
                                songs = emptyList(),
                                sectionType = if (type == "singles") ArtistSectionType.SINGLE_EP else ArtistSectionType.ALBUM
                            )
                            else -> null
                        }
                    }
                    _uiState.update {
                        it.copy(
                            allItems = it.allItems + mappedItems,
                            allItemsContinuation = page.continuation,
                            isAllItemsLoading = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            allItemsError = e.localizedMessage,
                            isAllItemsLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        allItemsError = e.localizedMessage,
                        isAllItemsLoading = false
                    )
                }
            }
        }
    }

    private var popularSongsAllJob: Job? = null

    fun loadAllPopularSongs() {
        val endpoint = _uiState.value.songsMoreEndpoint
        if (endpoint == null) {
            _uiState.update { it.copy(popularSongsAll = it.popularSongs, popularSongsAllContinuation = null, isPopularSongsAllLoading = false) }
            return
        }

        popularSongsAllJob?.cancel()
        popularSongsAllJob = viewModelScope.launch {
            _uiState.update { it.copy(isPopularSongsAllLoading = true, popularSongsAll = emptyList(), popularSongsAllContinuation = null, popularSongsAllError = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.artistItems(endpoint)
                }
                result.onSuccess { page ->
                    val mappedSongs = page.items.mapNotNull { item ->
                        (item as? SongItem)?.toNativeSong()
                    }
                    _uiState.update {
                        it.copy(
                            popularSongsAll = mappedSongs,
                            popularSongsAllContinuation = page.continuation,
                            isPopularSongsAllLoading = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            popularSongsAllError = e.localizedMessage,
                            isPopularSongsAllLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        popularSongsAllError = e.localizedMessage,
                        isPopularSongsAllLoading = false
                    )
                }
            }
        }
    }

    fun loadMorePopularSongs() {
        val continuation = _uiState.value.popularSongsAllContinuation ?: return
        if (_uiState.value.isPopularSongsAllLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isPopularSongsAllLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.artistItemsContinuation(continuation)
                }
                result.onSuccess { page ->
                    val mappedSongs = page.items.mapNotNull { item ->
                        (item as? SongItem)?.toNativeSong()
                    }
                    _uiState.update {
                        it.copy(
                            popularSongsAll = it.popularSongsAll + mappedSongs,
                            popularSongsAllContinuation = page.continuation,
                            isPopularSongsAllLoading = false
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            popularSongsAllError = e.localizedMessage,
                            isPopularSongsAllLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        popularSongsAllError = e.localizedMessage,
                        isPopularSongsAllLoading = false
                    )
                }
            }
        }
    }
}

private val songDisplayComparator = compareBy<Song> { it.discNumber ?: 1 }
    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
    .thenBy { it.title.lowercase() }

private fun buildAlbumSections(songs: List<Song>): List<ArtistAlbumSection> {
    if (songs.isEmpty()) return emptyList()

    val sections = songs
        .groupBy { it.albumId to it.album }
        .map { (key, albumSongs) ->
            val sortedSongs = albumSongs.sortedWith(songDisplayComparator)
            val albumYear = albumSongs.mapNotNull { song -> song.year.takeIf { it > 0 } }.maxOrNull()
            val albumArtUri = albumSongs.firstNotNullOfOrNull { it.albumArtUriString }
            ArtistAlbumSection(
                albumId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Album"),
                year = albumYear,
                albumArtUriString = albumArtUri,
                songs = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<ArtistAlbumSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
