package com.unshoo.pixelmusic.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.unshoo.pixelmusic.BuildConfig
import com.unshoo.pixelmusic.data.model.update.AppReleaseAsset
import com.unshoo.pixelmusic.data.model.update.AppReleaseInfo
import com.unshoo.pixelmusic.data.model.update.SemVerComparator
import com.unshoo.pixelmusic.data.model.update.UpdateCheckResult
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        const val GITHUB_REPO_OWNER = "ianshulyadav"
        const val GITHUB_REPO_NAME = "PixelMusicApp"
        const val GITHUB_LATEST_RELEASE_URL =
            "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        const val GITHUB_ATOM_FEED_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases.atom"
        const val GITHUB_WEB_LATEST_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
        const val GITHUB_RELEASES_WEB_URL =
            "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"
    }

    private val updateHttpClient: OkHttpClient by lazy {
        val resilientDns = Dns { hostname ->
            try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Secondary DNS lookup
                try {
                    InetAddress.getAllByName(hostname).toList().ifEmpty { throw e }
                } catch (fallbackEx: Exception) {
                    Timber.w(fallbackEx, "DNS resolution failed for $hostname")
                    throw fallbackEx
                }
            }
        }

        okHttpClient.newBuilder()
            .dns(resilientDns)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Checks if the device currently has active network connectivity.
     */
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Checks GitHub for the latest release.
     * @param force If true, bypasses ETag caching and forces a fresh network query.
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME

        if (!isNetworkAvailable()) {
            return@withContext UpdateCheckResult.Error(
                "No internet connection. Please check your network and try again."
            )
        }

        // Try primary GitHub REST API
        try {
            val apiResult = checkViaRestApi(currentVersion, force)
            if (apiResult != null) {
                return@withContext apiResult
            }
        } catch (e: Exception) {
            Timber.w(e, "Primary GitHub API failed, trying fallback...")
        }

        // Fallback 1: Try GitHub releases.atom feed
        try {
            val atomResult = checkViaAtomFeed(currentVersion)
            if (atomResult != null) {
                Timber.i("Retrieved release info via GitHub Atom feed fallback.")
                return@withContext atomResult
            }
        } catch (e: Exception) {
            Timber.w(e, "GitHub Atom feed fallback failed.")
        }

        // Fallback 2: Check cached release if available
        val cachedJson = userPreferencesRepository.cachedReleaseInfoFlow.first()
        if (!cachedJson.isNullOrBlank()) {
            val cachedRelease = runCatching {
                json.decodeFromString<AppReleaseInfo>(cachedJson)
            }.getOrNull()
            if (cachedRelease != null) {
                return@withContext processRelease(cachedRelease, currentVersion)
            }
        }

        return@withContext UpdateCheckResult.Error(
            "Could not connect to GitHub servers. Please check your connection."
        )
    }

    private suspend fun checkViaRestApi(currentVersion: String, force: Boolean): UpdateCheckResult? {
        val lastEtag = if (!force) userPreferencesRepository.lastUpdateEtagFlow.first() else ""

        val requestBuilder = Request.Builder()
            .url(GITHUB_LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "PixelMusic/$currentVersion (Android; Music Player)")

        if (lastEtag.isNotEmpty()) {
            requestBuilder.header("If-None-Match", lastEtag)
        }

        val request = requestBuilder.build()
        val response = updateHttpClient.newCall(request).execute()

        response.use { resp ->
            val now = System.currentTimeMillis()
            userPreferencesRepository.setLastUpdateCheckTime(now)

            if (resp.code == 304 && !force) {
                val cachedJson = userPreferencesRepository.cachedReleaseInfoFlow.first()
                if (!cachedJson.isNullOrBlank()) {
                    val cachedRelease = runCatching {
                        json.decodeFromString<AppReleaseInfo>(cachedJson)
                    }.getOrNull()

                    if (cachedRelease != null) {
                        return processRelease(cachedRelease, currentVersion)
                    }
                }
                return UpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    latestVersion = currentVersion
                )
            }

            if (!resp.isSuccessful) {
                Timber.w("checkViaRestApi: HTTP ${resp.code}")
                return null
            }

            val bodyString = resp.body?.string() ?: return null
            val releaseInfo = json.decodeFromString<AppReleaseInfo>(bodyString)

            val newEtag = resp.header("ETag")
            if (!newEtag.isNullOrBlank()) {
                userPreferencesRepository.setLastUpdateEtag(newEtag)
            }
            userPreferencesRepository.setCachedReleaseInfo(bodyString)

            return processRelease(releaseInfo, currentVersion)
        }
    }

    /**
     * Fallback that reads GitHub's unmetered XML/Atom feed.
     */
    private fun checkViaAtomFeed(currentVersion: String): UpdateCheckResult? {
        val request = Request.Builder()
            .url(GITHUB_ATOM_FEED_URL)
            .header("User-Agent", "PixelMusic/$currentVersion (Android; Music Player)")
            .build()

        val response = updateHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val xml = resp.body?.string() ?: return null

            // Extract tag from link or id e.g. <link rel="alternate" ... href=".../tag/v1.6.09"/>
            val tagMatch = Regex("""/releases/tag/([^"'\s>]+)""").find(xml) ?: return null
            val tagName = tagMatch.groupValues[1]

            val titleMatch = Regex("""<title>([^<]+)</title>""").findAll(xml)
                .map { it.groupValues[1] }
                .firstOrNull { it.contains("v", ignoreCase = true) } ?: tagName

            // Construct standard release assets based on release convention
            val standardAssets = listOf(
                AppReleaseAsset(
                    name = "app-arm64-v8a-release.apk",
                    downloadUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/app-arm64-v8a-release.apk",
                    architecture = "ARM64 (64-bit)"
                ),
                AppReleaseAsset(
                    name = "app-universal-release.apk",
                    downloadUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/app-universal-release.apk",
                    architecture = "Universal"
                ),
                AppReleaseAsset(
                    name = "app-armeabi-v7a-release.apk",
                    downloadUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/download/$tagName/app-armeabi-v7a-release.apk",
                    architecture = "ARMv7 (32-bit)"
                )
            )

            val release = AppReleaseInfo(
                tagName = tagName,
                name = titleMatch,
                body = "A new release ($tagName) is available on GitHub.",
                htmlUrl = "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/tag/$tagName",
                assets = standardAssets
            )

            return processRelease(release, currentVersion)
        }
    }

    /**
     * Determines if [release] is newer than [currentVersion] and selects the best matching APK.
     */
    fun processRelease(release: AppReleaseInfo, currentVersion: String): UpdateCheckResult {
        val remoteVersion = release.tagName.trim()
        val isNewer = SemVerComparator.isNewer(remoteVersion, currentVersion)

        return if (isNewer) {
            val matchingAsset = findMatchingAsset(release.assets)
            UpdateCheckResult.Available(
                release = release,
                currentVersion = currentVersion,
                targetAsset = matchingAsset
            )
        } else {
            UpdateCheckResult.UpToDate(
                currentVersion = currentVersion,
                latestVersion = remoteVersion
            )
        }
    }

    /**
     * Picks the most suitable APK asset for the current device architecture from [assets].
     */
    fun findMatchingAsset(assets: List<AppReleaseAsset>): AppReleaseAsset? {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        val supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()

        // 1. Try 64-bit ARM
        if (supportedAbis.any { it.contains("arm64", ignoreCase = true) }) {
            apkAssets.firstOrNull { it.name.contains("arm64", ignoreCase = true) }?.let {
                return it.copy(architecture = "ARM64 (64-bit)")
            }
        }

        // 2. Try 32-bit ARM
        if (supportedAbis.any { it.contains("armeabi", ignoreCase = true) || it.contains("armv7", ignoreCase = true) }) {
            apkAssets.firstOrNull {
                it.name.contains("armeabi-v7a", ignoreCase = true) || it.name.contains("armv7", ignoreCase = true)
            }?.let {
                return it.copy(architecture = "ARMv7 (32-bit)")
            }
        }

        // 3. Try x86_64
        if (supportedAbis.any { it.contains("x86_64", ignoreCase = true) }) {
            apkAssets.firstOrNull { it.name.contains("x86_64", ignoreCase = true) }?.let {
                return it.copy(architecture = "x86_64 (Intel/AMD)")
            }
        }

        // 4. Try universal APK
        apkAssets.firstOrNull { it.name.contains("universal", ignoreCase = true) }?.let {
            return it.copy(architecture = "Universal")
        }

        // 5. Fallback to first available APK
        val firstApk = apkAssets.first()
        return firstApk.copy(architecture = "Standard")
    }

    /**
     * Retrieves the cached release info if previously stored.
     */
    suspend fun getCachedReleaseInfo(): AppReleaseInfo? = withContext(Dispatchers.IO) {
        val jsonStr = userPreferencesRepository.cachedReleaseInfoFlow.first() ?: return@withContext null
        return@withContext runCatching { json.decodeFromString<AppReleaseInfo>(jsonStr) }.getOrNull()
    }
}
