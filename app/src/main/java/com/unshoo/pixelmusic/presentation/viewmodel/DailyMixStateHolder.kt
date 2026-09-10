package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
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
        val totalSongs = musicRepository.getSongCountFlow().first()
        if (totalSongs <= maxCandidates) return musicRepository.getAllSongsOnce()

        val pageSize = 200
        val pageCount = (maxCandidates + pageSize - 1) / pageSize
        val maxOffset = (totalSongs - pageSize).coerceAtLeast(0)
        val offsets = LinkedHashSet<Int>(pageCount)
        while (offsets.size < pageCount) {
            offsets += if (maxOffset == 0) 0 else Random.nextInt(maxOffset + 1)
        }

        return offsets.flatMap { offset ->
            musicRepository.getSongsPage(limit = pageSize, offset = offset)
        }.distinctBy { it.id }
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

    /**
     * Load persisted daily mix from storage using direct DB queries by IDs
     * instead of combining with the full allSongs flow.
     */
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
