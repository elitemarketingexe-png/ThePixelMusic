package com.unshoo.pixelmusic.utils

import kotlin.math.abs

/**
 * Centralized utilities for converting YouTube string IDs to Long IDs
 * used in the unified songs database.
 *
 * These offsets separate YouTube-sourced entities from MediaStore IDs
 * (which are always positive) by producing large negative values.
 */
object YouTubeIdUtils {

    const val YOUTUBE_SONG_ID_OFFSET = 15_000_000_000_000L
    const val YOUTUBE_ALBUM_ID_OFFSET = 16_000_000_000_000L
    const val YOUTUBE_ARTIST_ID_OFFSET = 17_000_000_000_000L

    /**
     * Converts a YouTube video ID (e.g. "dQw4w9WgXcQ") to a stable negative Long
     * for use as a unified song ID in the database.
     */
    fun toUnifiedSongId(youtubeId: String): Long {
        return -(YOUTUBE_SONG_ID_OFFSET + abs(youtubeId.hashCode().toLong()))
    }

    /**
     * Alias for toUnifiedSongId to match legacy naming across the codebase.
     */
    fun toUnifiedYoutubeSongId(youtubeId: String): Long = toUnifiedSongId(youtubeId)

    /**
     * Converts an album name to a stable negative Long for use as a unified album ID.
     */
    fun toUnifiedAlbumId(albumName: String): Long {
        return -(YOUTUBE_ALBUM_ID_OFFSET + abs(albumName.lowercase().hashCode().toLong()))
    }

    /**
     * Alias for toUnifiedAlbumId to match legacy naming across the codebase.
     */
    fun toUnifiedYoutubeAlbumId(albumName: String): Long = toUnifiedAlbumId(albumName)

    /**
     * Converts an artist name to a stable negative Long for use as a unified artist ID.
     */
    fun toUnifiedArtistId(artistName: String): Long {
        return -(YOUTUBE_ARTIST_ID_OFFSET + abs(artistName.lowercase().hashCode().toLong()))
    }

    /**
     * Alias for toUnifiedArtistId to match legacy naming across the codebase.
     */
    fun toUnifiedYoutubeArtistId(artistName: String): Long = toUnifiedArtistId(artistName)

    /**
     * Safely converts a Song.id (String) to a Long for database operations.
     *
     * Handles:
     * - Numeric string IDs from MediaStore (e.g. "12345") → parsed directly
     * - YouTube-prefixed IDs (e.g. "youtube_dQw4w9WgXcQ") → stripped and hashed to negative Long matching toUnifiedSongId
     * - Any other non-numeric string ID → hashed to negative Long
     */
    fun safeSongIdToLong(songId: String): Long {
        val numeric = songId.toLongOrNull()
        if (numeric != null) return numeric
        val cleanId = if (songId.startsWith("youtube_")) {
            songId.removePrefix("youtube_")
        } else songId
        return -(YOUTUBE_SONG_ID_OFFSET + abs(cleanId.hashCode().toLong()))
    }
}
