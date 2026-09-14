package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.BuildConfig
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedLastFmRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend fun getApiKey(): String {
        val userKey = runCatching { userPreferencesRepository.lastfmApiKeyFlow.first() }.getOrDefault("")
        return if (userKey.isNotBlank()) userKey else BuildConfig.LASTFM_API_KEY
    }

    private suspend fun call(method: String, params: Map<String, String>): JsonObject? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext null

        val urlBuilder = "https://ws.audioscrobbler.com/2.0/".toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        urlBuilder.addQueryParameter("method", method)
        urlBuilder.addQueryParameter("api_key", apiKey)
        urlBuilder.addQueryParameter("format", "json")
        for ((k, v) in params) {
            urlBuilder.addQueryParameter(k, v)
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "PixelMusic/1.0 (Android)")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val element = json.parseToJsonElement(body) as? JsonObject ?: return@use null
                if (element.containsKey("error")) null else element
            }
        }.getOrNull()
    }

    suspend fun fetchRecentTracks(username: String, limit: Int = 30): List<RecentTrack> = withContext(Dispatchers.IO) {
        if (username.isBlank() || username.equals("Guest User", ignoreCase = true)) return@withContext emptyList()
        val root = call("user.getrecenttracks", mapOf("user" to username, "limit" to limit.toString())) ?: return@withContext emptyList()
        val recentTracksObj = root["recenttracks"] as? JsonObject ?: return@withContext emptyList()
        val trackElem = recentTracksObj["track"]
        val trackList = GenerateJsonHelper.asObjectList(trackElem)

        trackList.filterNot { it["@attr"]?.jsonObject?.get("nowplaying") != null }
            .mapNotNull { obj ->
                val name = obj.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val artistName = obj.artistName()
                if (artistName.isBlank()) return@mapNotNull null
                val albumName = obj.albumName().orEmpty()
                val url = obj.stringOrNull("url").orEmpty()
                val images = (obj["image"] as? JsonArray)?.mapNotNull { imgElem ->
                    val imgObj = imgElem as? JsonObject ?: return@mapNotNull null
                    val imgUrl = imgObj.stringOrNull("#text") ?: return@mapNotNull null
                    val size = imgObj.stringOrNull("size").orEmpty()
                    ImageDto(imgUrl, size)
                }.orEmpty()

                val dateObj = obj["date"] as? JsonObject
                val date = dateObj?.let {
                    RecentTrackDate(
                        uts = it.stringOrNull("uts").orEmpty(),
                        text = it.stringOrNull("#text").orEmpty()
                    )
                }

                RecentTrack(
                    name = name,
                    artist = RecentTrackArtistRef(name = artistName),
                    album = RecentTrackArtistRef(name = albumName),
                    image = images,
                    url = url,
                    date = date
                )
            }
    }

    suspend fun fetchFriends(limit: Int = 20): List<FriendEntry> = withContext(Dispatchers.IO) {
        val username = runCatching { userPreferencesRepository.lastfmUsernameFlow.first() }.getOrDefault("")
        if (username.isBlank() || username.equals("Guest User", ignoreCase = true)) return@withContext emptyList()
        val root = call("user.getfriends", mapOf("user" to username, "limit" to limit.toString())) ?: return@withContext emptyList()
        val friendsObj = root["friends"] as? JsonObject ?: return@withContext emptyList()
        val userElem = friendsObj["user"]
        val userList = GenerateJsonHelper.asObjectList(userElem)

        userList.mapNotNull { obj ->
            val name = obj.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val realName = obj.stringOrNull("realname")
            val images = (obj["image"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            val avatar = images?.lastOrNull { ArtworkNormalizer.isRealImage(it.stringOrNull("#text")) }?.stringOrNull("#text")
            FriendEntry(
                name = name,
                displayName = realName?.takeIf(String::isNotBlank) ?: name,
                realName = realName,
                avatarUrl = avatar
            )
        }
    }

    suspend fun fetchTopAlbums(username: String, limit: Int = 20, period: String = "overall"): List<FeedTopAlbum> = withContext(Dispatchers.IO) {
        if (username.isBlank() || username.equals("Guest User", ignoreCase = true)) return@withContext emptyList()
        val root = call("user.gettopalbums", mapOf("user" to username, "limit" to limit.toString(), "period" to period)) ?: return@withContext emptyList()
        val topalbumsObj = root["topalbums"] as? JsonObject ?: return@withContext emptyList()
        val albumElem = topalbumsObj["album"]
        val albumList = GenerateJsonHelper.asObjectList(albumElem)

        albumList.mapNotNull { obj ->
            val name = obj.stringOrNull("name")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val artistObj = obj["artist"] as? JsonObject
            val artistName = artistObj?.stringOrNull("name") ?: obj.stringOrNull("artist") ?: ""
            if (artistName.isBlank() || artistName.equals("Unknown artist", ignoreCase = true)) return@mapNotNull null
            val playcount = obj.stringOrNull("playcount")?.toLongOrNull() ?: 0L
            val images = (obj["image"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            val bestArt = images?.lastOrNull { ArtworkNormalizer.isRealImage(it.stringOrNull("#text")) }?.stringOrNull("#text")

            FeedTopAlbum(
                name = name,
                artist = artistName,
                artworkUrl = bestArt,
                playCount = playcount
            )
        }
    }

    suspend fun callRaw(method: String, params: Map<String, String>): JsonObject? = call(method, params)
}

fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.artistName(): String {
    val a = this["artist"] ?: return ""
    return when (a) {
        is JsonPrimitive -> a.contentOrNull.orEmpty()
        is JsonObject -> a.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: a.stringOrNull("#text").orEmpty()
        else -> ""
    }
}

fun JsonObject.bestImageUrl(): String? {
    val images = (this["image"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return null
    fun bySize(size: String) = images.firstOrNull { it.stringOrNull("size") == size }
        ?.stringOrNull("#text")?.takeIf { ArtworkNormalizer.isRealImage(it) }
    return bySize("extralarge")
        ?: bySize("large")
        ?: bySize("medium")
        ?: images.firstOrNull { ArtworkNormalizer.isRealImage(it.stringOrNull("#text")) }?.stringOrNull("#text")
}

fun JsonObject.albumName(): String? {
    val a = this["album"] ?: return null
    return when (a) {
        is JsonPrimitive -> a.contentOrNull
        is JsonObject -> a.stringOrNull("#text")?.takeIf { it.isNotBlank() } ?: a.stringOrNull("title")
        else -> null
    }
}

object GenerateJsonHelper {
    fun asObjectList(element: JsonElement?): List<JsonObject> = when (element) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(element)
        else -> emptyList()
    }

    fun normalise(element: JsonElement?): List<GeneratedTrack> =
        asObjectList(element).mapNotNull { obj ->
            val name = obj.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = obj.artistName().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            GeneratedTrack(
                name = name,
                artist = artist,
                artworkUrl = obj.bestImageUrl(),
                url = obj.stringOrNull("url").orEmpty(),
                listeners = obj.stringOrNull("listeners")?.toLongOrNull(),
                playcount = obj.stringOrNull("playcount")?.toLongOrNull(),
                match = obj.stringOrNull("match")?.toDoubleOrNull(),
                album = obj.albumName(),
            )
        }

    fun namesOf(element: JsonElement?): List<String> =
        asObjectList(element).mapNotNull { it.stringOrNull("name")?.takeIf(String::isNotBlank) }
}
