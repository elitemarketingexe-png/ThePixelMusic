package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.PixelMusicSettings
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import kotlinx.coroutines.yield

/**
 * Remote data source for fetching YouTube playlist metadata and their songs
 * directly from the YouTube Music API via [YoutubeRequestHelper] + [YoutubeHelper].
 *
 * - [retrieveAll] fetches the list of playlists visible in the authenticated user's library.
 * - [retrieveOne] fetches the full song list for a single playlist, including continuations.
 *
 * Requires a valid authenticated [PixelMusicSettings] (cookies) passed from [DatastoreRepository].
 */
class YoutubePlaylistDataSource {

    /**
     * Returns all playlist info entries visible in the user's YouTube Music library.
     */
    fun retrieveAll(settings: PixelMusicSettings): List<PlaylistInfo> {
        return YoutubeHelper.extractPlaylists(
            YoutubeRequestHelper.browse(
                Constants.YoutubeApi.Browse.PLAYLIST_BROWSE_ID,
                settings
            ),
            settings
        )
    }

    /**
     * Fetches and returns a [Playlist] populated with its full song list from YouTube.
     * Uses the app's InnerTube parser because it handles playlist continuations reliably
     * for very large playlists (5k-20k+ songs). The older JSON helper only read page 1.
     */
    suspend fun retrieveOne(playlist: Playlist, settings: PixelMusicSettings): Playlist {
        val remoteCandidates = when {
            playlist.info.id == "liked_songs" -> listOf("LM")
            playlist.info.id == "LM" -> listOf("LM")
            playlist.info.id.startsWith("VL") -> listOf(playlist.info.id.removePrefix("VL"), playlist.info.id)
            else -> listOf(playlist.info.id, "VL${playlist.info.id}")
        }.distinct()

        for (remoteId in remoteCandidates) {
            val innerTubeResult = runCatching { retrieveOneViaInnerTube(playlist, remoteId) }.getOrNull()
            if (innerTubeResult != null && innerTubeResult.songs.isNotEmpty()) {
                return innerTubeResult
            }
        }

        // Fallback to existing browse extraction for unusual playlist renderers.
        var remoteSongs = emptyList<com.unshoo.pixelmusic.data.model.youtube.Song>()
        for (remoteId in remoteCandidates) {
            remoteSongs = runCatching {
                YoutubeHelper.extractSongList(
                    YoutubeRequestHelper.browse(remoteId, settings),
                    settings
                )
            }.getOrDefault(emptyList())
            if (remoteSongs.isNotEmpty()) break
        }

        return playlist.copy(
            info = playlist.info.copy(
                lastSyncSongCount = if (remoteSongs.isNotEmpty()) remoteSongs.size else playlist.info.lastSyncSongCount,
                lastSyncTimestamp = if (remoteSongs.isNotEmpty()) System.currentTimeMillis() else playlist.info.lastSyncTimestamp
            ),
            unsortedSongs = remoteSongs,
            crossRefs = remoteSongs.mapIndexed { index, song ->
                PlaylistSongCrossRef(playlist.info.id, song.youtubeId, index)
            }
        )
    }

    private suspend fun retrieveOneViaInnerTube(
        playlist: Playlist,
        remoteId: String
    ): Playlist {
        val page = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.playlist(remoteId).getOrThrow()
        val allSongs = ArrayList<unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem>(page.songs.size.coerceAtLeast(64))
        allSongs.addAll(page.songs)

        var continuation = page.songsContinuation ?: page.continuation
        var pages = 0
        val seenContinuations = HashSet<String>()

        while (!continuation.isNullOrBlank() && seenContinuations.add(continuation)) {
            yield()
            val continuationPage = unshoo.ianshulyadav.pixelmusic.innertube.YouTube
                .playlistContinuation(continuation)
                .getOrNull() ?: break
            if (continuationPage.songs.isEmpty() && continuationPage.continuation.isNullOrBlank()) break
            allSongs.addAll(continuationPage.songs)
            continuation = continuationPage.continuation
            pages++
            // Hard safety cap: enough for 20k+ songs at typical page sizes while preventing
            // runaway loops on malformed continuations.
            if (pages >= 1_200) break
        }

        val youtubeSongs = allSongs
            .map { it.toYoutubeSong() }

        val pagePlaylist = page.playlist
        return playlist.copy(
            info = playlist.info.copy(
                title = pagePlaylist.title.ifBlank { playlist.info.title },
                coverHref = pagePlaylist.thumbnail ?: playlist.info.coverHref,
                lastSyncSongCount = youtubeSongs.size.takeIf { it > 0 } ?: playlist.info.lastSyncSongCount,
                lastSyncTimestamp = System.currentTimeMillis()
            ),
            unsortedSongs = youtubeSongs.distinctBy { it.youtubeId },
            crossRefs = youtubeSongs.mapIndexed { index, song ->
                PlaylistSongCrossRef(playlist.info.id, song.youtubeId, index)
            }
        )
    }
}
