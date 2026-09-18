package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.utils.OfflineAudioResolver
import com.unshoo.pixelmusic.utils.YouTubeIdUtils
import com.unshoo.pixelmusic.data.remote.youtube.parseDurationStringToMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Generate and update daily/your mixes
 * - Persist and restore mix state
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    private suspend fun loadMixCandidates(maxCandidates: Int = 1_000): List<Song> {
        val isOnline = connectivityStateHolder.isOnline.value

        // Gather all confirmed offline playable songs: local media + verified downloaded songs
        val localSongs = musicRepository.getLocalSongsOnce()
        val downloadedSongs = try {
            com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
                .songRepository().getDownloadedSongs()
                .filter { ySong ->
                    val path = ySong.audioFilePath
                    path?.startsWith("content://") == true || (path != null && java.io.File(path).let { it.isFile && it.length() > 0L })
                }.map { ySong ->
                    val primaryArtistId = YouTubeIdUtils.toUnifiedYoutubeArtistId(ySong.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist")
                    val songAlbum = ySong.album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
                    Song(
                        id = "youtube_${ySong.youtubeId}",
                        title = ySong.title,
                        artist = ySong.artist,
                        artistId = primaryArtistId,
                        artists = listOf(
                            com.unshoo.pixelmusic.data.model.ArtistRef(
                                id = primaryArtistId,
                                name = ySong.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                                isPrimary = true
                            )
                        ),
                        album = songAlbum,
                        albumId = YouTubeIdUtils.toUnifiedYoutubeAlbumId(songAlbum),
                        albumArtist = null,
                        path = ySong.audioFilePath ?: "",
                        contentUriString = "youtube://${ySong.youtubeId}",
                        albumArtUriString = ySong.thumbnailPath ?: ySong.thumbnailHref,
                        duration = parseDurationStringToMillis(ySong.duration),
                        genre = ySong.genre ?: "YouTube Music",
                        lyrics = null,
                        isFavorite = false,
                        trackNumber = 0,
                        discNumber = null,
                        year = 0,
                        dateAdded = ySong.downloadTimestamp,
                        dateModified = 0,
                        mimeType = "audio/opus",
                        bitrate = null,
                        sampleRate = null,
                        youtubeId = ySong.youtubeId,
                        albumBrowseId = ySong.albumBrowseId
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }

        val offlinePlayable = (localSongs + downloadedSongs)
            .filter { OfflineAudioResolver.hasOfflineAudio(context, it) }
            .distinctBy { it.id }

        if (!isOnline) {
            // When offline, strictly generate from local + downloads
            return offlinePlayable
        }

        // When online, 100% guarantee local + downloaded tracks are included alongside library songs
        val totalSongs = musicRepository.getSongCountFlow().first()
        val librarySongs = if (totalSongs <= maxCandidates) {
            musicRepository.getAllSongsOnce()
        } else {
            val pageSize = 200
            val pageCount = (maxCandidates + pageSize - 1) / pageSize
            val maxOffset = (totalSongs - pageSize).coerceAtLeast(0)
            val offsets = LinkedHashSet<Int>(pageCount)
            while (offsets.size < pageCount) {
                offsets += if (maxOffset == 0) 0 else Random.nextInt(maxOffset + 1)
            }
            offsets.flatMap { offset ->
                musicRepository.getSongsPage(limit = pageSize, offset = offset)
            }
        }

        return (offlinePlayable + librarySongs).distinctBy { it.id }
    }

    /**
     * Update the daily mix with new songs.
     * Uses getAllSongsOnce() to load songs on-demand instead of keeping a permanent subscription.
     */
    fun updateDailyMix(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            val allSongs = loadMixCandidates()
            if (allSongs.isNotEmpty()) {
                val favoriteIds = favoriteSongIdsFlow.first()

                // Generate daily mix
                val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                // Generate your mix
                val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
            } else {
                // BUGFIX: clear BOTH lists on empty library, not just yourMix
                _yourMixSongs.value = persistentListOf()
                _dailyMixSongs.value = persistentListOf()
            }
        }
    }

    fun loadPersistedDailyMix() {
        // Load Daily Mix
        scope?.launch {
            val dailyMixIds = userPreferencesRepository.dailyMixSongIdsFlow.first()
            if (dailyMixIds.isNotEmpty() && _dailyMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIdsOnce(dailyMixIds)
                }
                if (songs.isNotEmpty()) {
                    // Maintain persisted order
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = dailyMixIds.mapNotNull { songMap[it] }
                    _dailyMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }

        // Load Your Mix
        scope?.launch {
            val yourMixIds = userPreferencesRepository.yourMixSongIdsFlow.first()
            if (yourMixIds.isNotEmpty() && _yourMixSongs.value.isEmpty()) {
                val songs = withContext(Dispatchers.IO) {
                    musicRepository.getSongsByIdsOnce(yourMixIds)
                }
                if (songs.isNotEmpty()) {
                    val songMap = songs.associateBy { it.id }
                    val orderedSongs = yourMixIds.mapNotNull { songMap[it] }
                    _yourMixSongs.value = orderedSongs.toImmutableList()
                }
            }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     * Atomically clears in-memory and persisted mixes, then recalculates using active library songs.
     */
    fun forceUpdate(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            // Immediately purge persisted DataStore IDs and reset timestamp
            userPreferencesRepository.clearDailyMixData()

            val allSongs = loadMixCandidates()
            if (allSongs.isNotEmpty()) {
                val favoriteIds = favoriteSongIdsFlow.first()

                // Generate daily mix
                val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                // Generate your mix
                val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
            } else {
                _yourMixSongs.value = persistentListOf()
                _dailyMixSongs.value = persistentListOf()
            }

            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(favoriteSongIdsFlow: kotlinx.coroutines.flow.Flow<Set<String>>) {
        scope?.launch(Dispatchers.IO) {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val now = Calendar.getInstance()
            val lastCal = Calendar.getInstance().apply { timeInMillis = lastUpdate }

            // Compare full date (year + day-of-year) to handle year boundaries
            // and epoch-zero (never updated) correctly.
            val isNewDay = lastUpdate <= 0L ||
                now.get(Calendar.YEAR) != lastCal.get(Calendar.YEAR) ||
                now.get(Calendar.DAY_OF_YEAR) != lastCal.get(Calendar.DAY_OF_YEAR)

            if (isNewDay) {
                updateDailyMix(favoriteSongIdsFlow)
                // BUGFIX: wait for generation to complete before marking day as done.
                updateJob?.join()
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    /**
     * Set the daily mix songs directly (used for AI-generated mixes).
     */
    fun setDailyMixSongs(songs: List<Song>) {
        _dailyMixSongs.value = songs.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Get a candidate pool for AI playlist generation.
     */
    suspend fun getCandidatePool(
        allSongs: List<Song>,
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> {
        return dailyMixManager.generateDailyMix(allSongs, favoriteIds, maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
