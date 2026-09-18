package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.ArtistRef
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem

fun upgradeThumbnailUrlToHighQuality(url: String?): String? {
    if (url.isNullOrBlank()) return url

    return when {
        url.contains("googleusercontent.com") || url.contains("ggpht.com") -> {
            url.replace(Regex("=w\\d+-h\\d+.*"), "=w1024-h1024-l90-rj")
                .replace(Regex("=s\\d+.*"), "=s1024")
                .let {
                    if (!it.contains("=w") && !it.contains("=s")) {
                        if (it.contains("=")) it.substringBeforeLast("=") + "=w1024-h1024-l90-rj"
                        else "$it=w1024-h1024-l90-rj"
                    } else it
                }
        }
        url.contains("i.ytimg.com") || url.contains("img.youtube.com") -> {
            val match = Regex("/vi(?:_webp)?/([^/]+)/").find(url)
            if (match != null) {
                val videoId = match.groupValues[1]
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            } else {
                url.substringBefore("?")
            }
        }
        else -> url
    }
}

fun SongItem.toYoutubeSong(): com.unshoo.pixelmusic.data.model.youtube.Song {
    val artistName = artists.joinToString { it.name }
    val totalSeconds = duration ?: 0
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val durationString = if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
    return com.unshoo.pixelmusic.data.model.youtube.Song(
        youtubeId = id,
        title = title,
        artist = artistName,
        album = album?.name,
        albumBrowseId = album?.id,
        duration = durationString,
        thumbnailHref = upgradeThumbnailUrlToHighQuality(thumbnail) ?: ""
    )
}

fun SongItem.toNativeSong(): Song {
    val rawArtistName = artists.joinToString { it.name }
    val artistNames = com.unshoo.pixelmusic.data.stream.CloudMusicUtils.parseArtistNames(rawArtistName)
    val artistRefs = artistNames.mapIndexed { index, name ->
        val originalArtist = artists.find { it.name.equals(name, ignoreCase = true) }
        val channelId = originalArtist?.id?.takeIf { it.isNotBlank() }
        val artistId = if (channelId != null) {
            com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedArtistIdFromChannelId(channelId)
        } else {
            com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedArtistId(name)
        }
        ArtistRef(
            id = artistId,
            name = name,
            isPrimary = index == 0,
            channelId = channelId
        )
    }
    val artistName = artistNames.joinToString(", ")
    val primaryArtistId = artistRefs.firstOrNull()?.id ?: 0L
    val songId = "youtube_$id"
    val albumName = album?.name?.takeIf { it.isNotBlank() } ?: "YouTube Music"
    val albumId = -(16_000_000_000_000L + kotlin.math.abs(albumName.lowercase().hashCode().toLong()))
    
    return Song(
        id = songId,
        title = title,
        artist = artistName,
        artistId = primaryArtistId,
        artists = artistRefs,
        album = albumName,
        albumId = albumId,
        albumArtist = artistName,
        path = "",
        contentUriString = "youtube://$id",
        albumArtUriString = upgradeThumbnailUrlToHighQuality(thumbnail),
        duration = (duration ?: 0) * 1000L,
        genre = "YouTube",
        lyrics = null,
        isFavorite = likeStatus == "LIKE",
        isDisliked = likeStatus == "DISLIKE",
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/opus",
        bitrate = 128000,
        sampleRate = 44100,
        telegramFileId = null,
        telegramChatId = null,
        neteaseId = null,
        gdriveFileId = null,
        qqMusicMid = null,
        navidromeId = null,
        jellyfinId = null,
        youtubeId = id,
        albumBrowseId = album?.id
    )
}

fun com.unshoo.pixelmusic.data.model.youtube.Song.toNativeSong(): Song {
    val artistNames = com.unshoo.pixelmusic.data.stream.CloudMusicUtils.parseArtistNames(artist)
    val artistRefs = artistNames.mapIndexed { index, name ->
        val artistId = -(17_000_000_000_000L + kotlin.math.abs(name.lowercase().hashCode().toLong()))
        ArtistRef(
            id = artistId,
            name = name,
            isPrimary = index == 0,
            channelId = null
        )
    }
    val artistName = artistNames.joinToString(", ")
    val primaryArtistId = artistRefs.firstOrNull()?.id ?: 0L
    val songId = "youtube_$youtubeId"
    val albumName = album?.takeIf { it.isNotBlank() } ?: "YouTube Music"
    val albumId = -(16_000_000_000_000L + kotlin.math.abs(albumName.lowercase().hashCode().toLong()))
    
    return Song(
        id = songId,
        title = title,
        artist = artistName,
        artistId = primaryArtistId,
        artists = artistRefs,
        album = albumName,
        albumId = albumId,
        albumArtist = artistName,
        path = audioFilePath.orEmpty(),
        contentUriString = "youtube://$youtubeId",
        albumArtUriString = upgradeThumbnailUrlToHighQuality(thumbnailPath ?: thumbnailHref),
        duration = parseDurationStringToMillis(duration),
        genre = "YouTube",
        lyrics = null,
        isFavorite = false,
        trackNumber = 0,
        discNumber = null,
        year = 0,
        dateAdded = System.currentTimeMillis(),
        dateModified = System.currentTimeMillis(),
        mimeType = "audio/opus",
        bitrate = 128000,
        sampleRate = 44100,
        telegramFileId = null,
        telegramChatId = null,
        neteaseId = null,
        gdriveFileId = null,
        qqMusicMid = null,
        navidromeId = null,
        jellyfinId = null,
        youtubeId = youtubeId,
        albumBrowseId = albumBrowseId
    )
}

internal fun parseDurationStringToMillis(durationStr: String): Long {
    if (durationStr.isBlank()) return 0L
    val parts = durationStr.split(":")
    return try {
        when (parts.size) {
            1 -> {
                val raw = parts[0].toLong()
                if (raw >= 1000L) raw else raw * 1000L
            }
            2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
            3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
            else -> 0L
        }
    } catch (e: Exception) {
        0L
    }
}
