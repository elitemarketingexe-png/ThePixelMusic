package com.unshoo.pixelmusic.data.remote.youtube

import androidx.media3.common.MediaItem
import com.unshoo.pixelmusic.data.model.youtube.Song

/**
 * Converts a nullable [MediaItem] back into a [Song] model.
 * Reads metadata and extras that were packed in via [Song.mediaItem].
 */
fun MediaItem?.toSong(): Song {
    val extras = this?.mediaMetadata?.extras
    val album = this?.mediaMetadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() }
        ?: extras?.getString("album")
    val albumBrowseId = extras?.getString("albumBrowseId")
    return Song(
        uid = extras?.getString(Constants.ExoPlayer.SongMetadata.UID) ?: "",
        youtubeId = this?.mediaId ?: "",
        title = this?.mediaMetadata?.title?.toString() ?: "",
        artist = this?.mediaMetadata?.artist?.toString() ?: "",
        album = album,
        albumBrowseId = albumBrowseId,
        thumbnailHref = upgradeThumbnailUrlToHighQuality(this?.mediaMetadata?.artworkUri?.toString()).orEmpty(),
        duration = extras?.getString(Constants.ExoPlayer.SongMetadata.DURATION) ?: ""
    )
}
