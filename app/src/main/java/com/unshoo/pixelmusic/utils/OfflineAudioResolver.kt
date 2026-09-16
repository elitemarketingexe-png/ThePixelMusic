package com.unshoo.pixelmusic.utils

import android.content.Context
import android.net.Uri
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import java.io.File
import java.util.Locale

/**
 * Single source of truth for checking if a song has verified 100% offline audio on disk,
 * and resolving alternative stream keys for deduplication.
 */
object OfflineAudioResolver {

    /**
     * Returns true if the song has an accessible offline audio source
     * (local file, content:// URI, or downloaded YouTube audio file).
     */
    fun hasOfflineAudio(
        context: Context,
        song: Song
    ): Boolean {
        // 1. Direct path check (local MediaStore / scanned folder / mapped file)
        if (song.path.isNotBlank()) {
            val file = File(song.path)
            if (file.exists() && file.length() > 0L) return true
        }

        // 2. Local URI check (content:// or file://)
        if (song.contentUriString.startsWith("file://")) {
            val parsedPath = Uri.parse(song.contentUriString).path
            if (!parsedPath.isNullOrBlank()) {
                val file = File(parsedPath)
                if (file.exists() && file.length() > 0L) return true
            }
        }
        if (song.contentUriString.startsWith("content://")) {
            return true
        }

        // 3. Downloaded YouTube audio check
        val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
        if (ytId != null) {
            val audioDir = PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
            val webmFile = File(audioDir, "$ytId.webm")
            if (webmFile.exists() && webmFile.length() > 0L) return true

            val possibleExtensions = listOf("m4a", "opus", "mp3")
            for (ext in possibleExtensions) {
                val f = File(audioDir, "$ytId.$ext")
                if (f.exists() && f.length() > 0L) return true
            }
        }

        return false
    }

    /**
     * Suspending version that also verifies cached Telegram files when telegramRepository is provided.
     */
    suspend fun hasOfflineAudioWithTelegram(
        context: Context,
        song: Song,
        telegramRepository: TelegramRepository? = null
    ): Boolean {
        if (hasOfflineAudio(context, song)) return true
        if (song.telegramFileId != null && song.telegramFileId > 0 && telegramRepository != null) {
            if (telegramRepository.isFileCached(song.telegramFileId)) return true
        }
        return false
    }

    /**
     * Resolves the offline File if present on disk with valid size, or null otherwise.
     */
    fun getOfflineAudioFile(context: Context, song: Song): File? {
        if (song.path.isNotBlank()) {
            val file = File(song.path)
            if (file.exists() && file.length() > 0L) return file
        }
        val ytId = song.youtubeId ?: if (song.id.startsWith("youtube_")) song.id.removePrefix("youtube_") else null
        if (ytId != null) {
            val audioDir = PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
            val webmFile = File(audioDir, "$ytId.webm")
            if (webmFile.exists() && webmFile.length() > 0L) return webmFile
            val possibleExtensions = listOf("m4a", "opus", "mp3")
            for (ext in possibleExtensions) {
                val f = File(audioDir, "$ytId.$ext")
                if (f.exists() && f.length() > 0L) return f
            }
        }
        return null
    }

    /**
     * Returns a normalized key for matching titles & artists across different sources (local, telegram, youtube).
     */
    fun alternativeKey(title: String, artist: String): String? {
        val normalizedTitle = title.normalizeMetadataText()
        val normalizedArtist = artist.normalizeMetadataText()
        if (normalizedTitle.isNullOrBlank() || normalizedArtist.isNullOrBlank()) return null
        return "${normalizedTitle.lowercase(Locale.ROOT)}|${normalizedArtist.lowercase(Locale.ROOT)}"
    }

    /**
     * Deduplicates a list of songs by matching alternativeKey (or song id if key is null).
     * Retains original insertion order using stdlib groupBy.
     */
    fun deduplicateQueue(
        songs: List<Song>,
        startSongId: String? = null
    ): Pair<List<Song>, String?> {
        if (songs.size <= 1) return Pair(songs, startSongId)

        var resolvedStartSongId = startSongId
        val groups = songs.groupBy { alternativeKey(it.title, it.artist) ?: it.id }

        val result = groups.values.map { duplicates ->
            if (duplicates.size == 1) duplicates[0]
            else {
                val matchStart = if (startSongId != null) duplicates.firstOrNull { it.id == startSongId } else null
                val chosen = matchStart ?: duplicates.maxByOrNull { song ->
                    when {
                        song.path.isNotBlank() || song.contentUriString.startsWith("content://") || song.contentUriString.startsWith("file://") -> 2
                        song.telegramFileId != null || song.contentUriString.startsWith("telegram:") -> 1
                        else -> 0
                    }
                } ?: duplicates[0]
                if (matchStart != null) resolvedStartSongId = chosen.id
                chosen
            }
        }

        return Pair(result, resolvedStartSongId)
    }
}
