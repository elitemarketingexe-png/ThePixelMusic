package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.DatastoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TASTE_PROFILE_TTL_MILLIS = 60L * 60 * 1000

@Singleton
class FeedTasteProfileProvider @Inject constructor(
    private val lastFm: FeedLastFmRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val innerTube: FeedInnerTubeApi,
    private val datastoreRepository: DatastoreRepository,
) {
    private val mutex = Mutex()
    private var cached: TasteProfile? = null
    private var cachedForUsername: String? = null
    private var cachedForYtAccount: String? = null

    private fun YouTubeMusicTrack.toGeneratedTrack() = GeneratedTrack(
        name = title,
        artist = artist,
        artworkUrl = artworkUrl,
        url = "https://music.youtube.com/watch?v=$videoId",
        album = album,
    )

    private suspend fun call(params: Map<String, String>): JsonObject? {
        val method = params["method"] ?: return null
        return lastFm.callRaw(method, params - "method")
    }

    suspend fun get(forceRefresh: Boolean = false): TasteProfile = mutex.withLock {
        val exploreLastFm = runCatching { userPreferencesRepository.exploreLastfmEnabledFlow.first() }.getOrDefault(true)
        val username = if (!exploreLastFm) "" else runCatching { userPreferencesRepository.lastfmUsernameFlow.first() }.getOrDefault("")
        val isGuest = username.isBlank() || username.equals("Guest User", ignoreCase = true)
        val cookies = runCatching { datastoreRepository.cookies.first() }.getOrNull()
        val isYtConnected = cookies?.toRawCookie()?.let {
            it.contains("SAPISID=") || it.contains("__Secure-3PAPISID=")
        } == true
        val ytAccountName = runCatching { datastoreRepository.ytUsername.first() }.getOrDefault("")
        val ytAccountKey = if (isYtConnected) ytAccountName else ""

        cached?.let {
            if (!forceRefresh && cachedForUsername == username && cachedForYtAccount == ytAccountKey &&
                System.currentTimeMillis() - it.builtAtMillis < TASTE_PROFILE_TTL_MILLIS
            ) {
                return@withLock it
            }
        }

        var topTracksRaw: List<GeneratedTrack> = emptyList()
        var recentRaw: List<GeneratedTrack> = emptyList()
        var topArtistNames: Set<String> = emptySet()
        var topArtistsRaw: List<String> = emptyList()
        var topTags: Set<String> = emptySet()
        var ytMusicRecentRaw: List<GeneratedTrack> = emptyList()
        var ytMusicLikedRaw: List<GeneratedTrack> = emptyList()
        var ytMusicFeedRaw: List<GeneratedTrack> = emptyList()

        coroutineScope {
            val ytTasteDeferred = async(Dispatchers.IO) {
                runCatching { innerTube.fetchTasteSignals() }.getOrNull()
            }
            if (!isGuest) {
                val topTracksDeferred = async(Dispatchers.IO) { call(mapOf("method" to "user.gettoptracks", "user" to username, "period" to "overall", "limit" to "50")) }
                val recentDeferred = async(Dispatchers.IO) { call(mapOf("method" to "user.getrecenttracks", "user" to username, "limit" to "50")) }
                val topArtistsDeferred = async(Dispatchers.IO) { call(mapOf("method" to "user.gettopartists", "user" to username, "period" to "overall", "limit" to "30")) }
                val topTagsDeferred = async(Dispatchers.IO) { call(mapOf("method" to "user.gettoptags", "user" to username, "limit" to "15")) }

                val topTracksResult = topTracksDeferred.await()
                val recentResult = recentDeferred.await()
                val topArtistsResult = topArtistsDeferred.await()
                val topTagsResult = topTagsDeferred.await()

                topTracksRaw = topTracksResult?.let { GenerateJsonHelper.normalise(it["toptracks"]?.jsonObject?.get("track")) } ?: emptyList()

                recentRaw = recentResult?.let { r ->
                    val raw = r["recenttracks"]?.jsonObject?.get("track")
                    val withoutNowPlaying = GenerateJsonHelper.asObjectList(raw)
                        .filterNot { it["@attr"]?.jsonObject?.get("nowplaying") != null }
                    GenerateJsonHelper.normalise(JsonArray(withoutNowPlaying))
                } ?: emptyList()

                topArtistsRaw = topArtistsResult
                    ?.let { GenerateJsonHelper.namesOf(it["topartists"]?.jsonObject?.get("artist")) }
                    ?: emptyList()
                topArtistNames = topArtistsRaw.map { it.trim().lowercase() }.toSet()

                topTags = topTagsResult
                    ?.let { GenerateJsonHelper.namesOf(it["toptags"]?.jsonObject?.get("tag")) }
                    ?.map { it.lowercase() }
                    ?.toSet() ?: emptySet()
            }
            val ytTaste = ytTasteDeferred.await()
            ytMusicRecentRaw = ytTaste?.recentTracks.orEmpty().map { it.toGeneratedTrack() }
            ytMusicLikedRaw = ytTaste?.likedTracks.orEmpty().map { it.toGeneratedTrack() }
            ytMusicFeedRaw = ytTaste?.feedTracks.orEmpty().map { it.toGeneratedTrack() }
        }

        val hadLastFmTaste = topTracksRaw.isNotEmpty() || recentRaw.isNotEmpty() ||
            topArtistNames.isNotEmpty() || topTags.isNotEmpty()
        val hasPersonalSignals = hadLastFmTaste || ytMusicRecentRaw.isNotEmpty() ||
            ytMusicLikedRaw.isNotEmpty() || ytMusicFeedRaw.isNotEmpty()

        // Seamless chart seeding fallback if user data is missing or guest
        if (!hasPersonalSignals) {
            coroutineScope {
                val chartTracksDeferred = async(Dispatchers.IO) { call(mapOf("method" to "chart.gettoptracks", "limit" to "50")) }
                val chartArtistsDeferred = async(Dispatchers.IO) { call(mapOf("method" to "chart.gettopartists", "limit" to "30")) }
                val chartTagsDeferred = async(Dispatchers.IO) { call(mapOf("method" to "chart.gettoptags", "limit" to "15")) }

                val chartTracksResult = chartTracksDeferred.await()
                val chartArtistsResult = chartArtistsDeferred.await()
                val chartTagsResult = chartTagsDeferred.await()

                topTracksRaw = chartTracksResult?.let { GenerateJsonHelper.normalise(it["tracks"]?.jsonObject?.get("track")) }
                    ?: chartTracksResult?.let { GenerateJsonHelper.normalise(it["toptracks"]?.jsonObject?.get("track")) }
                    ?: emptyList()
                recentRaw = topTracksRaw

                topArtistsRaw = chartArtistsResult?.let { GenerateJsonHelper.namesOf(it["artists"]?.jsonObject?.get("artist")) }
                    ?: emptyList()
                topArtistNames = topArtistsRaw.map { it.trim().lowercase() }.toSet()

                topTags = chartTagsResult?.let { GenerateJsonHelper.namesOf(it["tags"]?.jsonObject?.get("tag")) }
                    ?.map { it.lowercase() }?.toSet()
                    ?: setOf("rock", "indie", "pop", "electronic", "hip-hop", "synthwave", "alternative", "rnb", "jazz")
            }
        }

        val lastFmOrChartTop = topTracksRaw
        val lastFmOrChartRecent = recentRaw
        val lastFmOrChartTopArtists = topArtistsRaw
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

        topTracksRaw = (lastFmOrChartTop + ytMusicLikedRaw.take(8)).distinctBy { it.key }
        recentRaw = (lastFmOrChartRecent + ytMusicRecentRaw.take(10)).distinctBy { it.key }

        val affinity = mutableMapOf<String, Double>()
        fun addAffinity(artist: String, amount: Double) {
            val key = artist.trim().lowercase()
            if (key.isNotBlank()) affinity[key] = (affinity[key] ?: 0.0) + amount
        }
        lastFmOrChartTopArtists.take(30).forEachIndexed { index, artist ->
            addAffinity(artist, 1.45 / (1.0 + index / 12.0))
        }
        lastFmOrChartRecent.take(50).forEachIndexed { index, track ->
            addAffinity(track.artist, 1.30 / (1.0 + index / 14.0))
        }
        val maxTrackPlaycount = lastFmOrChartTop
            .mapNotNull { it.playcount }
            .maxOrNull()
            ?.takeIf { it > 0L }
            ?: 0L
        lastFmOrChartTop.take(50).forEachIndexed { index, track ->
            val playSignal = if (maxTrackPlaycount > 0L) {
                (track.playcount ?: 0L).toDouble() / maxTrackPlaycount.toDouble()
            } else 0.0
            addAffinity(
                track.artist,
                (0.75 + playSignal.coerceIn(0.0, 1.0) * 0.9) / (1.0 + index / 18.0),
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

        val resolvedTags = topTags.ifEmpty {
            if (hasPersonalSignals) emptySet()
            else setOf("rock", "indie", "pop", "electronic", "hip-hop", "synthwave", "alternative", "rnb")
        }

        val profile = TasteProfile(
            artistAffinity = normalizedAffinity,
            topTracksRaw = topTracksRaw,
            topArtistsRaw = topArtistsRaw,
            topTags = resolvedTags,
            hasPersonalSignals = hasPersonalSignals,
            builtAtMillis = System.currentTimeMillis(),
        )
        cached = profile
        cachedForUsername = username
        cachedForYtAccount = ytAccountKey
        profile
    }
}
