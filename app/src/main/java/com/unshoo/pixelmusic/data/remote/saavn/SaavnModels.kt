package com.unshoo.pixelmusic.data.remote.saavn

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

// ─── Data models for public client ──────────────────────────────────────────

@Serializable
data class SaavnDownloadUrl(
    @SerialName("quality") val quality: String = "",
    @SerialName("url") val url: String = ""
)

@Serializable
data class SaavnImage(
    @SerialName("quality") val quality: String = "",
    @SerialName("url") val url: String = ""
)

@Serializable
data class SaavnArtistItem(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = ""
)

@Serializable
data class SaavnArtists(
    @SerialName("primary") val primary: List<SaavnArtistItem> = emptyList(),
    @SerialName("featured") val featured: List<SaavnArtistItem> = emptyList(),
    @SerialName("all") val all: List<SaavnArtistItem> = emptyList()
)

@Serializable
data class SaavnAlbum(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class SaavnSong(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("duration") val duration: Int? = null,
    @SerialName("explicitContent") val explicitContent: Boolean = false,
    @SerialName("artists") val artists: SaavnArtists = SaavnArtists(),
    @SerialName("image") val image: List<SaavnImage> = emptyList(),
    @SerialName("downloadUrl") val downloadUrl: List<SaavnDownloadUrl> = emptyList(),
    @SerialName("album") val album: SaavnAlbum? = null,
    val isProOnly: Boolean = false
)

// ─── JioSaavn Raw API Response Models ───────────────────────────────────────

@Serializable
data class RawArtistMapItem(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val type: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
object LenientArtistListSerializer :
    JsonTransformingSerializer<List<RawArtistMapItem>>(ListSerializer(RawArtistMapItem.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return if (element is JsonArray) element else JsonArray(emptyList())
    }
}

@Serializable
data class RawArtistMap(
    @Serializable(with = LenientArtistListSerializer::class)
    @SerialName("primary_artists") val primaryArtists: List<RawArtistMapItem> = emptyList(),
    @Serializable(with = LenientArtistListSerializer::class)
    @SerialName("featured_artists") val featuredArtists: List<RawArtistMapItem> = emptyList(),
    @Serializable(with = LenientArtistListSerializer::class)
    val artists: List<RawArtistMapItem> = emptyList()
)

@Serializable
data class RawRights(
    val code: String = "",
    val cacheable: String = "",
    @SerialName("delete_cached_object") val deleteCachedObject: String = "",
    val reason: String = ""
) {
    val isProOnly: Boolean
        get() = code == "1" || reason.contains("Pro Only", ignoreCase = true)
}

@Serializable
data class RawMoreInfo(
    @SerialName("album_id") val albumId: String = "",
    val album: String = "",
    @SerialName("encrypted_media_url") val encryptedMediaUrl: String = "",
    val duration: String = "",
    val artistMap: RawArtistMap = RawArtistMap(),
    val rights: RawRights = RawRights()
)

@Serializable
data class RawSongItem(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val year: String = "",
    val image: String = "",
    val language: String = "",
    @SerialName("play_count") val playCount: String = "",
    @SerialName("explicit_content") val explicitContent: String = "",
    @SerialName("more_info") val moreInfo: RawMoreInfo = RawMoreInfo()
)

@Serializable
data class RawSearchResponse(
    val total: Int = 0,
    val start: Int = 0,
    val results: List<RawSongItem> = emptyList()
)
