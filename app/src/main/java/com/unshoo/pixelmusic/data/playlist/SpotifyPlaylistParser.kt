package com.unshoo.pixelmusic.data.playlist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SpotifyParsedTrack(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val spotifyUrl: String,
    val album: String = "",
    val coverUrl: String? = null
)

data class SpotifyParsedPlaylist(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val tracks: List<SpotifyParsedTrack>
)

object SpotifyPlaylistParser {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val PLAYLIST_URL_PATTERN = Pattern.compile(
        """(?:https?://open\.spotify\.com/(?:intl-[a-z]{2}/)?playlist/|spotify:playlist:)([a-zA-Z0-9]+)"""
    )
    private val NEXT_DATA_PATTERN = Pattern.compile(
        """<script\s+id="__NEXT_DATA__"\s+type="application/json">([^<]+)</script>"""
    )

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val matcher = PLAYLIST_URL_PATTERN.matcher(trimmed)
        if (matcher.find()) {
            return matcher.group(1)
        }
        // Direct alphanumeric ID check (typical Spotify ID is 22 chars)
        if (trimmed.matches(Regex("^[a-zA-Z0-9]{15,30}$"))) {
            return trimmed
        }
        return null
    }

    suspend fun parse(input: String): Result<SpotifyParsedPlaylist> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(input)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL or ID"))

        try {
            val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
            val request = Request.Builder()
                .url(embedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to fetch Spotify playlist (HTTP ${response.code})"))
            }

            val html = response.body?.string().orEmpty()
            val matcher = NEXT_DATA_PATTERN.matcher(html)
            if (!matcher.find()) {
                return@withContext Result.failure(Exception("Unable to extract playlist data from Spotify"))
            }

            val jsonContent = matcher.group(1) ?: return@withContext Result.failure(Exception("Empty playlist payload"))
            val root = JSONObject(jsonContent)
            val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")
                ?: return@withContext Result.failure(Exception("Invalid Spotify data structure"))

            val state = pageProps.optJSONObject("state")
            val entity = state?.optJSONObject("data")?.optJSONObject("entity")
                ?: return@withContext Result.failure(Exception("Playlist entity not found in Spotify data"))

            val title = entity.optString("name").ifBlank { entity.optString("title", "Imported Spotify Playlist") }
            val coverUrl: String? = run {
                // 1. Check visualIdentity.image array (highest resolution, e.g. 640x640)
                val viImages = entity.optJSONObject("visualIdentity")?.optJSONArray("image")
                if (viImages != null && viImages.length() > 0) {
                    var bestUrl: String? = null
                    var maxDim = -1
                    for (i in 0 until viImages.length()) {
                        val img = viImages.optJSONObject(i) ?: continue
                        val url = img.optString("url")
                        val w = img.optInt("maxWidth", 0)
                        if (url.isNotBlank() && w >= maxDim) {
                            maxDim = w
                            bestUrl = url
                        }
                    }
                    if (!bestUrl.isNullOrBlank()) return@run bestUrl
                }

                // 2. Check coverArt.sources array
                val coverArtObj = entity.optJSONObject("coverArt")
                val caSources = coverArtObj?.optJSONArray("sources")
                if (caSources != null && caSources.length() > 0) {
                    val url = caSources.optJSONObject(0)?.optString("url")
                    if (!url.isNullOrBlank()) return@run url
                }

                // 3. Check entity.images array
                val images = entity.optJSONArray("images")
                if (images != null && images.length() > 0) {
                    val url = images.optJSONObject(0)?.optString("url")
                    if (!url.isNullOrBlank()) return@run url
                }

                // 4. Direct coverArt string if present
                val directCoverArt = entity.optString("coverArt")
                if (directCoverArt.isNotBlank() && directCoverArt.startsWith("http")) {
                    return@run directCoverArt
                }

                // 5. Check og:image in HTML as fallback
                val ogMatcher = Pattern.compile("""<meta\s+(?:property|name)=["'](?:og:image|twitter:image)["']\s+content=["']([^"']+)["']""").matcher(html)
                if (ogMatcher.find()) {
                    val ogUrl = ogMatcher.group(1)
                    if (!ogUrl.isNullOrBlank()) return@run ogUrl
                }

                null
            }

            val tracks = mutableListOf<SpotifyParsedTrack>()
            val trackList = entity.optJSONArray("trackList")
            if (trackList != null) {
                for (i in 0 until trackList.length()) {
                    val item = trackList.optJSONObject(i) ?: continue
                    val trackTitle = item.optString("title").trim()
                    val artist = item.optString("subtitle").trim()
                    val durationMs = item.optLong("duration", 0L)
                    val uri = item.optString("uri")
                    val trackId = uri.substringAfterLast(":", "")
                    val spotifyUrl = if (trackId.isNotBlank()) "https://open.spotify.com/track/$trackId" else ""

                    if (trackTitle.isNotBlank()) {
                        tracks.add(
                            SpotifyParsedTrack(
                                title = trackTitle,
                                artist = artist.ifBlank { "Unknown Artist" },
                                durationMs = durationMs,
                                spotifyUrl = spotifyUrl,
                                coverUrl = coverUrl
                            )
                        )
                    }
                }
            }

            // Check if there are more tracks and we have an anonymous session token to paginate
            val totalCount = entity.optJSONObject("trackList")?.optInt("total") ?: tracks.size
            val accessToken = state.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")

            if (tracks.size < totalCount && !accessToken.isNullOrBlank()) {
                fetchRemainingTracks(playlistId, accessToken, tracks.size, tracks)
            }

            Result.success(
                SpotifyParsedPlaylist(
                    id = playlistId,
                    title = title,
                    coverUrl = coverUrl.takeIf { !it.isNullOrBlank() },
                    tracks = tracks
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "SpotifyPlaylistParser: Error parsing playlist $playlistId")
            Result.failure(e)
        }
    }

    private fun fetchRemainingTracks(
        playlistId: String,
        accessToken: String,
        startOffset: Int,
        outTracks: MutableList<SpotifyParsedTrack>
    ) {
        var offset = startOffset
        val limit = 100
        var hasMore = true

        while (hasMore) {
            try {
                val apiUrl = "https://api.spotify.com/v1/playlists/$playlistId/tracks?offset=$offset&limit=$limit"
                val apiReq = Request.Builder()
                    .url(apiUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val apiRes = httpClient.newCall(apiReq).execute()
                if (!apiRes.isSuccessful) break

                val bodyStr = apiRes.body?.string().orEmpty()
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("items") ?: break

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val track = item.optJSONObject("track") ?: continue
                    val trackTitle = track.optString("name").trim()
                    if (trackTitle.isBlank()) continue

                    val artistsArr = track.optJSONArray("artists")
                    val artistsList = mutableListOf<String>()
                    if (artistsArr != null) {
                        for (a in 0 until artistsArr.length()) {
                            val aObj = artistsArr.optJSONObject(a)
                            val name = aObj?.optString("name")
                            if (!name.isNullOrBlank()) artistsList.add(name)
                        }
                    }
                    val artistStr = artistsList.joinToString(", ").ifBlank { "Unknown Artist" }
                    val durationMs = track.optLong("duration_ms", 0L)
                    val trackId = track.optString("id")
                    val spotifyUrl = if (trackId.isNotBlank()) "https://open.spotify.com/track/$trackId" else ""
                    val albumObj = track.optJSONObject("album")
                    val albumName = albumObj?.optString("name").orEmpty()
                    val cover = albumObj?.optJSONArray("images")?.optJSONObject(0)?.optString("url")

                    outTracks.add(
                        SpotifyParsedTrack(
                            title = trackTitle,
                            artist = artistStr,
                            durationMs = durationMs,
                            spotifyUrl = spotifyUrl,
                            album = albumName,
                            coverUrl = cover
                        )
                    )
                }

                val total = json.optInt("total", outTracks.size)
                offset += items.length()
                hasMore = items.length() == limit && offset < total
            } catch (e: Exception) {
                Timber.w(e, "SpotifyPlaylistParser: Error fetching page at offset $offset")
                break
            }
        }
    }
}
