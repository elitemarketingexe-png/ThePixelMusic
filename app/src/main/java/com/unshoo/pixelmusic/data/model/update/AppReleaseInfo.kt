package com.unshoo.pixelmusic.data.model.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppReleaseAsset(
    @SerialName("name")
    val name: String,
    @SerialName("browser_download_url")
    val downloadUrl: String,
    @SerialName("size")
    val sizeBytes: Long = 0L,
    @SerialName("content_type")
    val contentType: String = "",
    val architecture: String = ""
)

@Serializable
data class AppReleaseInfo(
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("name")
    val name: String? = null,
    @SerialName("body")
    val body: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("published_at")
    val publishedAt: String = "",
    @SerialName("assets")
    val assets: List<AppReleaseAsset> = emptyList()
)

sealed interface UpdateCheckResult {
    data class Available(
        val release: AppReleaseInfo,
        val currentVersion: String,
        val targetAsset: AppReleaseAsset?
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String
    ) : UpdateCheckResult

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : UpdateCheckResult
}

/**
 * Robust SemVer comparator that handles 'v' prefixes, zero-padded segments,
 * and pre-release identifiers (e.g. "v1.6.09" vs "1.6.10", "1.6.10-beta1").
 */
object SemVerComparator {

    /**
     * Returns true if [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        return compare(remoteVersion, currentVersion) > 0
    }

    /**
     * Compares two versions.
     * Returns > 0 if v1 > v2, 0 if v1 == v2, < 0 if v1 < v2.
     */
    fun compare(v1: String, v2: String): Int {
        val clean1 = cleanVersion(v1)
        val clean2 = cleanVersion(v2)

        val parts1 = clean1.first
        val parts2 = clean2.first

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }

        // If numeric parts match, handle pre-release tag:
        // A release without pre-release is newer than a pre-release (e.g. 1.6.10 > 1.6.10-beta)
        val pre1 = clean1.second
        val pre2 = clean2.second

        return when {
            pre1.isEmpty() && pre2.isNotEmpty() -> 1
            pre1.isNotEmpty() && pre2.isEmpty() -> -1
            pre1.isNotEmpty() && pre2.isNotEmpty() -> pre1.compareTo(pre2)
            else -> 0
        }
    }

    private fun cleanVersion(version: String): Pair<List<Int>, String> {
        val trimmed = version.trim().removePrefix("v").removePrefix("V")
        val dashIndex = trimmed.indexOf('-')
        val numericPart = if (dashIndex != -1) trimmed.substring(0, dashIndex) else trimmed
        val preRelease = if (dashIndex != -1) trimmed.substring(dashIndex + 1).trim() else ""

        val segments = numericPart.split('.').mapNotNull { segment ->
            segment.trim().toIntOrNull()
        }

        return segments to preRelease
    }
}
