package com.unshoo.pixelmusic.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.R
import java.util.Locale

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val title: String,
    val defaultSort: SortOption,
    @StringRes val titleRes: Int
) {
    SONGS("SONGS", "SONGS", SortOption.SongTitleAZ, R.string.tab_songs),
    ALBUMS("ALBUMS", "ALBUMS", SortOption.AlbumTitleAZ, R.string.tab_albums),
    ARTISTS("ARTIST", "ARTIST", SortOption.ArtistNameAZ, R.string.tab_artists),
    PLAYLISTS("PLAYLISTS", "PLAYLISTS", SortOption.PlaylistNameAZ, R.string.tab_playlists),
    FOLDERS("FOLDERS", "FOLDERS", SortOption.FolderNameAZ, R.string.tab_folders),
    LIKED("LIKED", "LIKED", SortOption.LikedSongDateLiked, R.string.tab_liked);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
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

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }