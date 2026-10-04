package com.unshoo.pixelmusic.presentation.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.SortOption
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Stable identifiers for each library tab. The [stableKey] value is persisted so it must not
 * change between app versions.
 */
enum class LibraryTabId(
    val stableKey: String,
    val label: String,
    val sortOptions: List<SortOption>,
    @StringRes val titleRes: Int
) {
    Songs(
        stableKey = "SONGS",
        label = "SONGS",
        sortOptions = listOf(
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        ),
        titleRes = R.string.tab_songs
    ),
    Albums(
        stableKey = "ALBUMS",
        label = "ALBUMS",
        sortOptions = listOf(
            SortOption.AlbumTitleAZ,
            SortOption.AlbumTitleZA,
            SortOption.AlbumArtist,
            SortOption.AlbumArtistDesc,
            SortOption.AlbumReleaseYear,
            SortOption.AlbumReleaseYearAsc,
            SortOption.AlbumDateAdded
        ),
        titleRes = R.string.tab_albums
    ),
    Artists(
        stableKey = "ARTIST",
        label = "ARTIST",
        sortOptions = listOf(
            SortOption.ArtistNameAZ,
            SortOption.ArtistNameZA,
            SortOption.ArtistNumSongsDesc,
            SortOption.ArtistNumSongsAsc
        ),
        titleRes = R.string.tab_artists
    ),
    Playlists(
        stableKey = "PLAYLISTS",
        label = "PLAYLISTS",
        sortOptions = listOf(
            SortOption.PlaylistNameAZ,
            SortOption.PlaylistNameZA,
            SortOption.PlaylistDateCreated,
            SortOption.PlaylistDateCreatedAsc
        ),
        titleRes = R.string.tab_playlists
    ),
    Folders(
        stableKey = "FOLDERS",
        label = "FOLDERS",
        sortOptions = listOf(
            SortOption.FolderNameAZ,
            SortOption.FolderNameZA,
            SortOption.FolderSongCountAsc,
            SortOption.FolderSongCountDesc,
            SortOption.FolderSubdirCountAsc,
            SortOption.FolderSubdirCountDesc
        ),
        titleRes = R.string.tab_folders
    ),
    Liked(
        stableKey = "LIKED",
        label = "LIKED",
        sortOptions = listOf(
            SortOption.LikedSongTitleAZ,
            SortOption.LikedSongTitleZA,
            SortOption.LikedSongArtist,
            SortOption.LikedSongArtistDesc,
            SortOption.LikedSongAlbum,
            SortOption.LikedSongAlbumDesc,
            SortOption.LikedSongDateLiked,
            SortOption.LikedSongDateLikedAsc
        ),
        titleRes = R.string.tab_liked
    );

    companion object {
        val defaultOrder: List<LibraryTabId> = entries.toList()

        fun fromStableKey(key: String): LibraryTabId? = entries.firstOrNull { it.stableKey == key }
    }
}

@Composable
fun LibraryTabId.getDisplayTitle(): String {
    val raw = stringResource(titleRes)
    return remember(raw) {
        raw.lowercase().replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
        }
    }
}

internal fun decodeLibraryTabOrder(orderJson: String?): List<LibraryTabId> {
    val storedKeys = orderJson?.let {
        runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
    } ?: emptyList()

    val ordered = LinkedHashSet<LibraryTabId>()
    storedKeys.mapNotNull { LibraryTabId.fromStableKey(it) }.forEach { ordered.add(it) }
    LibraryTabId.defaultOrder.forEach { ordered.add(it) }
    return ordered.toList()
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull {
        it.stableKey.equals(this, ignoreCase = true) ||
            it.name.equals(this, ignoreCase = true) ||
            it.label.equals(this, ignoreCase = true)
    }