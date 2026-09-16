package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TASTE_PROFILE_TTL_MILLIS = 60L * 60 * 1000

@Singleton
class FeedTasteProfileProvider @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val innerTube: FeedInnerTubeApi,
    private val datastoreRepository: DatastoreRepository,
    private val playbackStatsRepository: PlaybackStatsRepository,
) {
    private val mutex = Mutex()
    private var cached: TasteProfile? = null
    private var cachedForYtAccount: String? = null

    private fun YouTubeMusicTrack.toGeneratedTrack() = GeneratedTrack(
        name = title,
        artist = artist,
        artworkUrl = artworkUrl,
        url = "https://music.youtube.com/watch?v=$videoId",
        album = album,
    )

    suspend fun get(forceRefresh: Boolean = false): TasteProfile = mutex.withLock {
        val cookies = runCatching { datastoreRepository.cookies.first() }.getOrNull()
        val isYtConnected = cookies?.toRawCookie()?.let {
            it.contains("SAPISID=") || it.contains("__Secure-3PAPISID=")
        } == true
        val ytAccountName = runCatching { datastoreRepository.ytUsername.first() }.getOrDefault("")
        val ytAccountKey = if (isYtConnected) ytAccountName else ""

        cached?.let {
            if (!forceRefresh && cachedForYtAccount == ytAccountKey &&
                System.currentTimeMillis() - it.builtAtMillis < TASTE_PROFILE_TTL_MILLIS
            ) {
                return@withLock it
            }
        }

        var topTracksRaw: List<GeneratedTrack> = emptyList()
        var recentRaw: List<GeneratedTrack> = emptyList()
        var topArtistNames: Set<String> = emptySet()
        var topArtistsRaw: List<String> = emptyList()
        var ytMusicRecentRaw: List<GeneratedTrack> = emptyList()
        var ytMusicLikedRaw: List<GeneratedTrack> = emptyList()
        var ytMusicFeedRaw: List<GeneratedTrack> = emptyList()

        coroutineScope {
            val ytTasteDeferred = async(Dispatchers.IO) {
                if (isYtConnected) {
                    runCatching { innerTube.fetchTasteSignals() }.getOrNull()
                } else null
            }
            val localHistoryDeferred = async(Dispatchers.IO) {
                runCatching { playbackStatsRepository.loadPlaybackHistory(limit = 60) }.getOrDefault(emptyList())
            }

            val ytTaste = ytTasteDeferred.await()
            ytMusicRecentRaw = ytTaste?.recentTracks.orEmpty().map { it.toGeneratedTrack() }
            ytMusicLikedRaw = ytTaste?.likedTracks.orEmpty().map { it.toGeneratedTrack() }
            ytMusicFeedRaw = ytTaste?.feedTracks.orEmpty().map { it.toGeneratedTrack() }

            val localHistory = localHistoryDeferred.await()
            if (localHistory.isNotEmpty()) {
                val localTrackCounts = localHistory
                    .filter { !it.title.isNullOrBlank() }
                    .groupingBy { (it.title?.trim()?.lowercase() ?: "") to (it.artist?.trim()?.lowercase() ?: "") }
                    .eachCount()

                val localTopTracks = localHistory
                    .distinctBy { (it.title?.trim()?.lowercase() ?: "") to (it.artist?.trim()?.lowercase() ?: "") }
                    .sortedByDescending { localTrackCounts[(it.title?.trim()?.lowercase() ?: "") to (it.artist?.trim()?.lowercase() ?: "")] ?: 1 }
                    .take(30)
                    .map { entry ->
                        GeneratedTrack(
                            name = entry.title ?: "",
                            artist = entry.artist ?: "",
                            artworkUrl = entry.thumbnail,
                            url = if (entry.songId.startsWith("http") || entry.songId.length == 11) "https://www.youtube.com/watch?v=${entry.songId}" else entry.songId,
                            album = null
                        )
                    }

                val localArtistCounts = localHistory
                    .mapNotNull { it.artist?.takeIf(String::isNotBlank) }
                    .filter { !it.equals("Unknown artist", ignoreCase = true) }
                    .groupingBy { it.trim().lowercase() }
                    .eachCount()

                val localTopArtists = localHistory
                    .mapNotNull { it.artist?.takeIf(String::isNotBlank) }
                    .filter { !it.equals("Unknown artist", ignoreCase = true) }
                    .distinctBy { it.trim().lowercase() }
                    .sortedByDescending { localArtistCounts[it.trim().lowercase()] ?: 1 }
                    .take(20)
                    .map { it.trim() }

                topTracksRaw = localTopTracks
                recentRaw = localTopTracks
                topArtistsRaw = localTopArtists
                topArtistNames = localTopArtists.map { it.lowercase() }.toSet()
            }
        }

        val ytArtistNames = (ytMusicRecentRaw + ytMusicLikedRaw + ytMusicFeedRaw)
            .filter { it.artist.isNotBlank() }
            .groupingBy { it.artist.trim() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
        topArtistsRaw = (topArtistsRaw + ytArtistNames.take(8))
            .distinctBy { it.lowercase() }
        topArtistNames = topArtistsRaw.map { it.trim().lowercase() }.toSet()

        topTracksRaw = (topTracksRaw + ytMusicLikedRaw.take(8)).distinctBy { it.key }
        recentRaw = (recentRaw + ytMusicRecentRaw.take(10)).distinctBy { it.key }

        val hasPersonalSignals = ytMusicRecentRaw.isNotEmpty() || ytMusicLikedRaw.isNotEmpty() ||
            ytMusicFeedRaw.isNotEmpty() || topTracksRaw.isNotEmpty() || topArtistsRaw.isNotEmpty()

        val affinity = mutableMapOf<String, Double>()
        fun addAffinity(artist: String, amount: Double) {
            val key = artist.trim().lowercase()
            if (key.isNotBlank()) affinity[key] = (affinity[key] ?: 0.0) + amount
        }
        topArtistsRaw.take(30).forEachIndexed { index, artist ->
            addAffinity(artist, 1.45 / (1.0 + index / 12.0))
        }
        recentRaw.take(50).forEachIndexed { index, track ->
            addAffinity(track.artist, 1.30 / (1.0 + index / 14.0))
        }
        topTracksRaw.take(50).forEachIndexed { index, track ->
            addAffinity(
                track.artist,
                1.1 / (1.0 + index / 18.0),
            )
        }
        val ytAffinity = mutableMapOf<String, Double>()
        fun addYtAffinity(artist: String, amount: Double) {
            val key = artist.trim().lowercase()
            if (key.isNotBlank()) ytAffinity[key] = (ytAffinity[key] ?: 0.0) + amount
        }
        ytMusicRecentRaw.forEachIndexed { index, track ->
            addYtAffinity(track.artist, 0.48 / (1.0 + index / 12.0))
        }
        ytMusicLikedRaw.forEachIndexed { index, track ->
            addYtAffinity(track.artist, 0.32 / (1.0 + index / 16.0))
        }
        ytMusicFeedRaw.forEachIndexed { index, track ->
            addYtAffinity(track.artist, 0.38 / (1.0 + index / 18.0))
        }
        ytAffinity.forEach { (artist, value) ->
            addAffinity(artist, value.coerceAtMost(1.4))
        }
        val maxAffinity = affinity.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val normalizedAffinity = affinity.mapValues { (_, value) -> (value / maxAffinity).coerceIn(0.0, 1.0) }

        val profile = TasteProfile(
            artistAffinity = normalizedAffinity,
            topTracksRaw = topTracksRaw,
            topArtistsRaw = topArtistsRaw,
            topTags = emptySet(),
            hasPersonalSignals = hasPersonalSignals,
            builtAtMillis = System.currentTimeMillis(),
        )
        cached = profile
        cachedForYtAccount = ytAccountKey
        profile
    }
}
