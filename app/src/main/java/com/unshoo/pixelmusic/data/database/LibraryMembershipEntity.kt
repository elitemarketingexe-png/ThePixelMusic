package com.unshoo.pixelmusic.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Append-only record of "this song has been played at least once, so it belongs in the library".
 *
 * Splitting this signal out from song_engagements stops the InvalidationTracker feedback loop:
 * song_engagements counters change on every play, while library_membership is only written
 * via INSERT OR IGNORE, which fires no triggers and invalidates no queries on subsequent plays.
 */
@Entity(tableName = "library_membership")
data class LibraryMembershipEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_key")
    val songKey: String,

    @ColumnInfo(name = "first_played_timestamp")
    val firstPlayedTimestamp: Long = 0L
)

/** Length of the "youtube_" token that prefixes a YouTube engagement song_id. */
private const val YOUTUBE_SONG_ID_PREFIX = "youtube_"

/** Length of the "youtube://" scheme that prefixes a YouTube content_uri_string. */
private const val YOUTUBE_URI_PREFIX = "youtube://"

/**
 * Normalizes an engagement song_id into a [LibraryMembershipEntity.songKey].
 *
 * Prefix-anchored and case-sensitive, matching String.removePrefix.
 */
internal fun String.toLibraryMembershipKey(): String =
    if (startsWith(YOUTUBE_SONG_ID_PREFIX)) {
        YOUTUBE_URI_PREFIX + substring(YOUTUBE_SONG_ID_PREFIX.length)
    } else {
        this
    }
