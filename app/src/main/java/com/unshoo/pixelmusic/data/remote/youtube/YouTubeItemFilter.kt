package com.unshoo.pixelmusic.data.remote.youtube

/**
 * Centralized filter for identifying podcasts, podcast shelves, and "Episodes for you" playlists
 * across all YouTube Music ingest points (SyncWorker, YouTubeLibrarySyncManager, PlaylistPreferencesRepository).
 */
object YouTubeItemFilter {

    fun isPodcastOrEpisode(title: String?, id: String?): Boolean {
        val cleanTitle = title?.trim().orEmpty()
        val cleanId = id?.trim().orEmpty()

        if (cleanTitle.contains("Episodes for you", ignoreCase = true) ||
            cleanTitle.contains("Episode", ignoreCase = true) ||
            cleanTitle.contains("Podcast", ignoreCase = true) ||
            cleanTitle.equals("Episodes", ignoreCase = true)
        ) {
            return true
        }

        if (cleanId.startsWith("SE", ignoreCase = false) ||
            cleanId.contains("episode", ignoreCase = true) ||
            cleanId.contains("podcast", ignoreCase = true)
        ) {
            return true
        }

        return false
    }

    fun isMusicPlaylist(title: String?, id: String?): Boolean = !isPodcastOrEpisode(title, id)
}
