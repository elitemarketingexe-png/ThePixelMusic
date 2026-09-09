package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import com.unshoo.pixelmusic.presentation.model.ExploreChipUiModel
import com.unshoo.pixelmusic.presentation.model.ExploreItemUiModel
import com.unshoo.pixelmusic.presentation.model.ExploreSectionUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContinuationLoading: Boolean = false,
    val isChartsLoading: Boolean = false,
    val homePageSections: List<HomePage.Section> = emptyList(),
    val homePageContinuation: String? = null,
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val chartsPage: ChartsPage? = null,
    val error: String? = null,
    val selectedFilter: String = "All",
    val recentMixes: List<Playlist> = emptyList(),
    val libraryPlaylists: List<Playlist> = emptyList(),
    val moodChips: List<HomePage.Chip> = emptyList(),
    val explorePageSections: List<HomePage.Section> = emptyList(),
    val activeMoodChip: HomePage.Chip? = null,
    val localSongs: Map<String, Song> = emptyMap()
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val listeningStatsTracker: ListeningStatsTracker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // --- Fine-grained decoupled StateFlows for high reactivity ---
    private val _sectionsState = MutableStateFlow<List<ExploreSectionUiModel>>(emptyList())
    val sectionsState: StateFlow<List<ExploreSectionUiModel>> = _sectionsState.asStateFlow()

    private val _moodChipsState = MutableStateFlow<List<ExploreChipUiModel>>(emptyList())
    val moodChipsState: StateFlow<List<ExploreChipUiModel>> = _moodChipsState.asStateFlow()

    private val _activeChipState = MutableStateFlow<ExploreChipUiModel?>(null)
    val activeChipState: StateFlow<ExploreChipUiModel?> = _activeChipState.asStateFlow()

    private val _isLoadingState = MutableStateFlow(true)
    val isLoadingState: StateFlow<Boolean> = _isLoadingState.asStateFlow()

    private val _isRefreshingState = MutableStateFlow(false)
    val isRefreshingState: StateFlow<Boolean> = _isRefreshingState.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    // Backward-compatible monolithic state
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var stage2Job: Job? = null

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(YTItem::class.java, YTItemTypeAdapter())
            .create()
    }

    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "explore_cache.json")
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                restoreFromCache()
            }
            loadDataInternal(forceRefresh = false)
        }
        viewModelScope.launch {
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val mixes = playlists.filter {
                    (it.source == "LASTFM_MIX" || it.source == "AI" || it.isAiGenerated) &&
                            it.songIds.isNotEmpty() &&
                            !it.name.contains("deleted", ignoreCase = true)
                }.sortedByDescending { it.lastModified }
                val libPlaylists = playlists.filter { !it.isQueueGenerated && it.id != "_downloaded_" && it.source != "LASTFM_MIX" && it.source != "AI" && !it.isAiGenerated }
                    .sortedByDescending { it.lastModified }
                _uiState.update { it.copy(recentMixes = mixes, libraryPlaylists = libPlaylists) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.advancedExplorePageFlow
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    loadData(forceRefresh = true)
                }
        }
        viewModelScope.launch {
            YouTube.personalizedExploreEnabled = runCatching {
                userPreferencesRepository.youtubePersonalizedExploreFlow.first()
            }.getOrDefault(false)

            userPreferencesRepository.youtubePersonalizedExploreFlow
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    YouTube.personalizedExploreEnabled = enabled
                    if (cacheFile.exists()) {
                        cacheFile.delete()
                    }
                    loadData(forceRefresh = true)
                }
        }
        viewModelScope.launch {
            userPreferencesRepository.youtubePersonalizedQueueFlow
                .distinctUntilChanged()
                .collect { enabled ->
                    YouTube.personalizedQueueEnabled = enabled
                }
        }
    }

    private fun restoreFromCache() {
        try {
            if (cacheFile.exists()) {
                if (cacheFile.length() > 512 * 1024) {
                    cacheFile.delete()
                    return
                }
                val json = cacheFile.readText()
                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                if (cache != null && cache.sections.isNotEmpty()) {
                    val age = System.currentTimeMillis() - cache.timestamp
                    if (age < 12 * 60 * 60 * 1000L) {
                        val uiSections = cache.sections.map { it.toUiModel() }
                        _sectionsState.value = uiSections
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                homePageSections = cache.sections
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore explore data from cache")
            try { cacheFile.delete() } catch (_: Exception) {}
        }
    }

    private fun persistToCache(sections: List<HomePage.Section>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cache = ExploreCacheModel(
                    sections = sections.take(20),
                    albums = emptyList(),
                    charts = null,
                    continuation = null,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(cache)
                if (json.length < 400 * 1024) {
                    cacheFile.writeText(json)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist explore data to cache")
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadDataInternal(forceRefresh)
        }
    }

    private suspend fun loadDataInternal(forceRefresh: Boolean) {
        stage2Job?.cancel()

        if (forceRefresh) {
            _isRefreshingState.value = true
            _errorState.value = null
            _uiState.update { it.copy(isRefreshing = true, error = null) }
        } else {
            val hasData = _sectionsState.value.isNotEmpty()
            _isLoadingState.value = !hasData
            _errorState.value = null
            _uiState.update { it.copy(isLoading = !hasData, error = null) }
        }

        try {
            // Stage 1: Single fast fetch for YouTube Home (personalized by user session)
            val home = withContext(Dispatchers.IO) {
                runCatching { YouTube.home().getOrNull() }.getOrNull()
            }

            if (home != null) {
                val isAdvancedExploreEnabled = userPreferencesRepository.advancedExplorePageFlow.first()
                val collectedSections = home.sections.toMutableList()
                var currentContinuation = if (isAdvancedExploreEnabled) home.continuation else null

                // Fetch initial 2 additional batches of continuation if advanced explore page is enabled
                var continuationBatches = 0
                while (isAdvancedExploreEnabled && !currentContinuation.isNullOrBlank() && continuationBatches < 2) {
                    continuationBatches++
                    val continuationPage = withContext(Dispatchers.IO) {
                        runCatching { YouTube.home(continuation = currentContinuation).getOrNull() }.getOrNull()
                    }
                    if (continuationPage != null && continuationPage.sections.isNotEmpty()) {
                        collectedSections.addAll(continuationPage.sections)
                        currentContinuation = continuationPage.continuation
                    } else {
                        break
                    }
                }

                val rawSections = collectedSections.filter { section ->
                    val title = section.title.lowercase()
                    !title.contains("new music videos") &&
                    !title.contains("trending") &&
                    !title.contains("long listens") &&
                    !title.contains("local") &&
                    !title.contains("quick picks") &&
                    !title.contains("quickpicks")
                }.distinctBy { it.title }

                // Extract personalized new releases directly from user's YouTube Home feed and account chips
                var personalizedNewReleases = rawSections.filter { section ->
                    val t = section.title.lowercase()
                    !t.contains("video") && !t.contains("videos") && (
                        t.contains("new release") || t.contains("new releases") ||
                        t.contains("new album") || t.contains("latest release") ||
                        t.contains("new music") || t.contains("recent release") ||
                        t.contains("novedades") || t.contains("nouveautés") ||
                        t.contains("veröffentlichungen") || t.contains("release radar") ||
                        t.contains("new for you")
                    )
                }.flatMap { it.items }.mapNotNull { item ->
                    when (item) {
                        is AlbumItem -> item
                        is PlaylistItem -> AlbumItem(
                            browseId = item.id,
                            playlistId = item.id,
                            title = item.title,
                            artists = listOfNotNull(item.author),
                            year = null,
                            thumbnail = item.thumbnail ?: "",
                            explicit = false
                        )
                        else -> null
                    }
                }.distinctBy { it.browseId }

                // If home page section didn't contain new releases directly, resolve user's account "New Releases" chip endpoint
                if (personalizedNewReleases.isEmpty()) {
                    val newReleaseChip = home.chips?.find { chip ->
                        val ct = chip.title.lowercase()
                        !ct.contains("video") && !ct.contains("videos") && (
                            ct.contains("new release") || ct.contains("new releases") || ct == "new" || ct == "nouveautés" || ct == "novedades"
                        )
                    }
                    val chipEndpoint = newReleaseChip?.endpoint
                    if (chipEndpoint != null) {
                        val fetchedExplore = runCatching {
                            YouTube.explore(browseId = chipEndpoint.browseId, params = chipEndpoint.params).getOrNull()
                        }.getOrNull()
                        if (fetchedExplore != null) {
                            personalizedNewReleases = fetchedExplore.sections.flatMap { it.items }.mapNotNull { item ->
                                when (item) {
                                    is AlbumItem -> item
                                    is PlaylistItem -> AlbumItem(
                                        browseId = item.id,
                                        playlistId = item.id,
                                        title = item.title,
                                        artists = listOfNotNull(item.author),
                                        year = null,
                                        thumbnail = item.thumbnail ?: "",
                                        explicit = false
                                    )
                                    else -> null
                                }
                            }.distinctBy { it.browseId }
                        }
                    }
                }

                // Progressive streaming: map to domain UI models once
                val uiSections = rawSections.map { it.toUiModel() }
                val rawChips = home.chips ?: emptyList()
                val uiChips = rawChips.map { ExploreChipUiModel(it.title, it.endpoint?.browseId, it.endpoint?.params) }

                _sectionsState.value = uiSections
                _moodChipsState.value = uiChips
                _isLoadingState.value = false
                _isRefreshingState.value = false

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isContinuationLoading = false,
                        homePageSections = rawSections,
                        homePageContinuation = currentContinuation,
                        newReleaseAlbums = personalizedNewReleases,
                        moodChips = rawChips
                    )
                }

                prefetchThumbnails(rawSections)
                persistToCache(rawSections)
            } else if (_sectionsState.value.isEmpty()) {
                val msg = "Failed to fetch explore data. Check connection."
                _errorState.value = msg
                _isLoadingState.value = false
                _isRefreshingState.value = false
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = msg) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading Explore screen data")
            val msg = e.localizedMessage ?: "Unknown error occurred"
            _errorState.value = msg
            _isLoadingState.value = false
            _isRefreshingState.value = false
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = msg) }
        }
    }

    private fun prefetchThumbnails(sections: List<HomePage.Section>) {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(500) // Defer prefetching until after initial UI layout render
            val loader = context.imageLoader
            sections.flatMap { it.items }.take(4).forEach { item ->
                val url = when (item) {
                    is SongItem -> item.thumbnail
                    is AlbumItem -> item.thumbnail
                    is PlaylistItem -> item.thumbnail
                    is ArtistItem -> item.thumbnail
                    else -> null
                }
                if (!url.isNullOrBlank()) {
                    val req = ImageRequest.Builder(context)
                        .data(url)
                        .size(64)
                        .build()
                    loader.enqueue(req)
                }
            }
        }
    }

    fun loadChartsIfNeeded(forceRefresh: Boolean = false) {
        if (!forceRefresh && (_uiState.value.chartsPage != null || _uiState.value.isChartsLoading)) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChartsLoading = true) }
            val charts = runCatching { YouTube.getChartsPage().getOrNull() }.getOrNull()
            _uiState.update { it.copy(isChartsLoading = false, chartsPage = charts) }
        }
    }

    fun loadMore() {
        val continuation = _uiState.value.homePageContinuation ?: return
        if (_uiState.value.isContinuationLoading || _uiState.value.isLoading || _uiState.value.isRefreshing) return

        viewModelScope.launch {
            if (!userPreferencesRepository.advancedExplorePageFlow.first()) return@launch
            _uiState.update { it.copy(isContinuationLoading = true) }
            val continuationHome = withContext(Dispatchers.IO) {
                runCatching { YouTube.home(continuation = continuation).getOrNull() }.getOrNull()
            }

            if (continuationHome != null && continuationHome.sections.isNotEmpty()) {
                val newRawSections = continuationHome.sections.filter { section ->
                    val title = section.title.lowercase()
                    !title.contains("new music videos") &&
                    !title.contains("trending") &&
                    !title.contains("long listens") &&
                    !title.contains("local") &&
                    !title.contains("quick picks") &&
                    !title.contains("quickpicks")
                }

                val currentRaw = _uiState.value.homePageSections
                val mergedRaw = (currentRaw + newRawSections).distinctBy { it.title }
                val uiSections = mergedRaw.map { it.toUiModel() }

                _sectionsState.value = uiSections
                _uiState.update {
                    it.copy(
                        isContinuationLoading = false,
                        homePageSections = mergedRaw,
                        homePageContinuation = continuationHome.continuation
                    )
                }
                persistToCache(mergedRaw)
            } else {
                _uiState.update { it.copy(isContinuationLoading = false, homePageContinuation = null) }
            }
        }
    }

    fun setSelectedFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter, activeMoodChip = null) }
        _activeChipState.value = null
        if (filter == "All") {
            loadData(forceRefresh = false)
        }
    }

    fun selectMoodChip(chip: HomePage.Chip?) {
        viewModelScope.launch {
            _activeChipState.value = chip?.let { ExploreChipUiModel(it.title, it.endpoint?.browseId, it.endpoint?.params) }
            _uiState.update { it.copy(activeMoodChip = chip, isLoading = true, error = null) }
            _isLoadingState.value = true

            if (chip == null) {
                loadDataInternal(false)
            } else {
                withContext(Dispatchers.IO) {
                    val endpoint = chip.endpoint
                    if (endpoint != null) {
                        YouTube.explore(browseId = endpoint.browseId, params = endpoint.params).onSuccess { exp ->
                            val rawSections = exp.sections
                            val uiSections = rawSections.map { it.toUiModel() }
                            _sectionsState.value = uiSections
                            _isLoadingState.value = false

                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    explorePageSections = rawSections
                                )
                            }
                            prefetchThumbnails(rawSections)
                        }.onFailure { e ->
                            val msg = "Failed to fetch mood feed: ${e.message}"
                            _errorState.value = msg
                            _isLoadingState.value = false
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = msg
                                )
                            }
                        }
                    } else {
                        _isLoadingState.value = false
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    private fun HomePage.Section.toUiModel(): ExploreSectionUiModel {
        val uiItems = items.mapNotNull { item ->
            when (item) {
                is SongItem -> ExploreItemUiModel.SongModel(item.toNativeSong(), item)
                is AlbumItem -> ExploreItemUiModel.AlbumModel(item)
                is PlaylistItem -> ExploreItemUiModel.PlaylistModel(item)
                is ArtistItem -> ExploreItemUiModel.ArtistModel(item)
                else -> null
            }
        }
        return ExploreSectionUiModel(
            id = title,
            title = title,
            label = label,
            items = uiItems
        )
    }
}

@androidx.annotation.Keep
data class ExploreCacheModel(
    val sections: List<HomePage.Section>,
    val albums: List<AlbumItem>,
    val charts: ChartsPage?,
    val continuation: String?,
    val timestamp: Long
)

private class YTItemTypeAdapter : com.google.gson.JsonSerializer<YTItem>, com.google.gson.JsonDeserializer<YTItem> {
    override fun serialize(src: YTItem, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val obj = com.google.gson.JsonObject()
        when (src) {
            is SongItem -> {
                obj.addProperty("type", "song")
                obj.add("data", context.serialize(src, SongItem::class.java))
            }
            is AlbumItem -> {
                obj.addProperty("type", "album")
                obj.add("data", context.serialize(src, AlbumItem::class.java))
            }
            is PlaylistItem -> {
                obj.addProperty("type", "playlist")
                obj.add("data", context.serialize(src, PlaylistItem::class.java))
            }
            is ArtistItem -> {
                obj.addProperty("type", "artist")
                obj.add("data", context.serialize(src, ArtistItem::class.java))
            }
        }
        return obj
    }

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): YTItem? {
        return try {
            val obj = json.asJsonObject
            val type = obj.get("type")?.asString ?: return null
            val targetClass = when (type) {
                "song", "SongItem" -> SongItem::class.java
                "album", "AlbumItem" -> AlbumItem::class.java
                "playlist", "PlaylistItem" -> PlaylistItem::class.java
                "artist", "ArtistItem" -> ArtistItem::class.java
                else -> null
            } ?: return null
            val data = obj.get("data") ?: obj
            context.deserialize(data, targetClass)
        } catch (_: Exception) {
            null
        }
    }
}
