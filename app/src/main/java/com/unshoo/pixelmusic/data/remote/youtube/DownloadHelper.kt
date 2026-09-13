package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.intPreferencesKey
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.preferences.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

object DownloadHelper {
    private val client = YoutubeHelper.client.newBuilder()
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun downloadImage(context: Context, imageUrl: String, id: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val imageDir =
                    PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.THUMBNAILS_FOLDER)
                val imageFile = File(imageDir, "$id.jpg")

                if (imageFile.exists()) {
                    PixelMusicHelper.printd("Song Image $id was already downloaded")
                    return@withContext imageFile
                }

                URL(imageUrl).openStream().use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                imageFile

            } catch (e: Exception) {
                PixelMusicHelper.printe(
                    tag = "PlaylistDownloadWorker",
                    message = "Error Downloading Thumbnail",
                    exception = e
                )
                null
            }
        }
    }

    suspend fun downloadAudio(
        context: Context,
        song: Song,
        connections: Int = 8
    ): String? = withContext(Dispatchers.IO) {

        val repo = DatastoreRepository(context)
        val customPath = repo.customDownloadPath.first()
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
        val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
        val fileName = "$safeTitle - $safeArtist.webm"

        if (customPath.isNotBlank()) {
            try {
                val treeUri = Uri.parse(customPath)
                val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                val existingFile = documentDir?.findFile(fileName)
                if (existingFile != null && existingFile.exists()) {
                    return@withContext existingFile.uri.toString()
                }
            } catch (e: Exception) {
                PixelMusicHelper.printe("Error checking custom download path exists: ${e.message}")
            }
        }

        val audioDir =
            PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        val outputFile = File(audioDir, "${song.youtubeId}.webm")

        if (outputFile.exists() && outputFile.length() > 0) {
            return@withContext outputFile.absolutePath
        }

        val tempFile = File(audioDir, "${song.youtubeId}.tmp")

        fun cleanupTempFile() {
            try { tempFile.delete() } catch (_: Exception) {}
        }

        val maxRetries = 3
        var lastException: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                // Invalidate any cached/expired stream URL to guarantee fresh highest-quality URL
                YoutubeHelper.invalidateStreamCache(song.youtubeId)
                val url = YoutubeHelper.getSongPlayerUrlWithQuality(context, song, maxBitrateKbps = 0)
                if (url.isBlank()) {
                    throw IOException("Empty stream URL for song ${song.youtubeId}")
                }

                // Determine total length for multi-part parallel downloading if requested
                var totalLength: Long = -1L
                try {
                    val headReq = Request.Builder()
                        .url(url)
                        .header("User-Agent", Constants.YoutubeApi.USER_AGENT)
                        .header("Range", "bytes=0-0")
                        .build()

                    client.newCall(headReq).execute().use { res ->
                        val contentRange = res.header("Content-Range")
                        if (contentRange != null && contentRange.contains("/")) {
                            totalLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        }
                    }
                } catch (_: Exception) {}

                val numConnections = connections.coerceIn(1, 16)
                if (totalLength > 0 && numConnections > 1) {
                    val partFiles = (0 until numConnections).map { i -> File(audioDir, "${song.youtubeId}.part$i") }
                    fun cleanupPartFiles() {
                        partFiles.forEach { runCatching { it.delete() } }
                    }

                    try {
                        val chunkSize = totalLength / numConnections
                        (0 until numConnections).map { i ->
                            async {
                                val start = i * chunkSize
                                val end = if (i == numConnections - 1) totalLength - 1 else (start + chunkSize - 1)
                                val partFile = partFiles[i]

                                val req = Request.Builder()
                                    .url(url)
                                    .header("User-Agent", Constants.YoutubeApi.USER_AGENT)
                                    .header("Range", "bytes=$start-$end")
                                    .header("Accept", "*/*")
                                    .build()

                                client.newCall(req).execute().use { response ->
                                    if (!response.isSuccessful && response.code != 206) {
                                        throw IOException("Failed chunk $i (HTTP ${response.code})")
                                    }
                                    response.body?.byteStream()?.use { input ->
                                        FileOutputStream(partFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    } ?: throw IOException("Empty body for chunk $i")
                                }
                            }
                        }.awaitAll()

                        FileOutputStream(tempFile).use { out ->
                            partFiles.forEach { part ->
                                part.inputStream().use { it.copyTo(out) }
                            }
                        }
                        cleanupPartFiles()
                    } catch (e: Exception) {
                        cleanupPartFiles()
                        throw e
                    }
                } else {
                    // Single stream download with Range header
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", Constants.YoutubeApi.USER_AGENT)
                        .header("Range", "bytes=0-")
                        .header("Accept", "*/*")
                        .build()

                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful && response.code != 206) {
                            throw IOException("Failed download (HTTP ${response.code}) for song ${song.youtubeId}")
                        }

                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IOException("Empty response body for song ${song.youtubeId}")
                    }
                }

                if (!tempFile.exists() || tempFile.length() <= 0) {
                    throw IOException("Downloaded file is zero bytes")
                }

                // Copy to custom path if set
                if (customPath.isNotBlank()) {
                    val treeUri = Uri.parse(customPath)
                    val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                    val file = documentDir?.createFile("audio/webm", fileName)
                    val outputUri = file?.uri
                    if (outputUri != null) {
                        context.contentResolver.openOutputStream(outputUri)?.use { out ->
                            tempFile.inputStream().use { it.copyTo(out) }
                        }
                        cleanupTempFile()
                        return@withContext outputUri.toString()
                    }
                }

                // Finished downloading audio file successfully
                cleanupTempFile()

                return@withContext outputFile.absolutePath

            } catch (e: CancellationException) {
                cleanupTempFile()
                throw e
            } catch (e: Exception) {
                lastException = e
                cleanupTempFile()
                PixelMusicHelper.printe("Download attempt $attempt/$maxRetries failed for ${song.title}: ${e.message}")
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(attempt * 600L)
                }
            }
        }

        PixelMusicHelper.printe("All $maxRetries download attempts failed for ${song.title}")
        return@withContext null
    }

    private suspend fun enforceStorageLimit(context: Context, keepFile: File? = null) = withContext(Dispatchers.IO) {
        // User downloaded songs and thumbnails are permanent offline media and must NOT be silently deleted.
        // Only clean up dangling temporary files (.tmp / .part) here.
        val audioDir = PixelMusicHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        audioDir.listFiles()?.filter { it.name.endsWith(".tmp") || it.name.contains(".part") }?.forEach { temp ->
            try { temp.delete() } catch (_: Exception) {}
        }
    }

    suspend fun copyToPublicDownload(context: Context, sourceFilePath: String, songTitle: String, artistName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null

            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val safeArtist = artistName.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val fileName = "$safeTitle - $safeArtist.webm"

            val repo = DatastoreRepository(context)
            val customPath = repo.customDownloadPath.first()
            val publicDownloadDir = if (customPath.isNotBlank()) {
                File(customPath)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PixelMusic")
            }
            if (!publicDownloadDir.exists()) {
                publicDownloadDir.mkdirs()
            }
            val destinationFile = File(publicDownloadDir, fileName)

            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("audio/webm"),
                null
            )

            return destinationFile
        } catch (e: Exception) {
            PixelMusicHelper.printe("Failed to copy to public downloads: ${e.message}", exception = e)
            return null
        }
    }
}
