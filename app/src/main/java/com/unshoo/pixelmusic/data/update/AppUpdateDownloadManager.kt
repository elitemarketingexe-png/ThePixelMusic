package com.unshoo.pixelmusic.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateDownloadState
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

@Singleton
class AppUpdateDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Downloads the APK file from [downloadUrl] and launches installation.
     */
    fun startDownload(downloadUrl: String, fileName: String) {
        if (_downloadState.value is UpdateDownloadState.Downloading) {
            Timber.d("Download already in progress.")
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            try {
                _downloadState.value = UpdateDownloadState.Downloading(0f, 0L, 0L)

                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val targetFile = File(updatesDir, fileName)

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                // Validate URL
                if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
                    Timber.e("Invalid download URL: $downloadUrl")
                    _downloadState.value = UpdateDownloadState.Error("Invalid download URL")
                    return@launch
                }

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "PixelMusic (Android; Package Downloader)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    _downloadState.value = UpdateDownloadState.Error("HTTP download failed: ${response.code}")
                    return@launch
                }

                val body = response.body

                val totalLength = if (body.contentLength() > 0) body.contentLength() else 45_000_000L
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdate = 0L

                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || totalBytesRead == totalLength) {
                                lastProgressUpdate = now
                                val progress = if (totalLength > 0) totalBytesRead.toFloat() / totalLength else 0f
                                _downloadState.value = UpdateDownloadState.Downloading(
                                    progress = progress.coerceIn(0f, 1f),
                                    bytesDownloaded = totalBytesRead,
                                    totalBytes = totalLength
                                )
                            }
                        }
                    }
                }

                Timber.i("APK downloaded successfully: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile)

                // Launch package installer
                withContext(Dispatchers.Main) {
                    installApk(targetFile)
                }
            } catch (e: Exception) {
                Timber.e(e, "APK download failed")
                _downloadState.value = UpdateDownloadState.Error(e.localizedMessage ?: "Download failed")
            }
        }
    }

    /**
     * Prompts the system package installer to install the downloaded APK.
     */
    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Timber.e("installApk: APK file does not exist: ${apkFile.absolutePath}")
                _downloadState.value = UpdateDownloadState.Error("APK file not found")
                return
            }



            // Check Unknown Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Timber.w("canRequestPackageInstalls is false. Opening Unknown App Sources settings.")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch package installer")
            _downloadState.value = UpdateDownloadState.Error("Could not launch installer: ${e.message}")
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun resetState() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}
