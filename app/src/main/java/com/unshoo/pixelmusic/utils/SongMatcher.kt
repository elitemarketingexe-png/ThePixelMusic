package com.unshoo.pixelmusic.utils

import java.util.Locale
import kotlin.math.abs

/**
 * Utility for fuzzy and normalized matching of songs across sources (YouTube, Local MediaStore, etc.).
 *
 * Primary matching criteria:
 * - Normalized title (stripping video/audio noise tags like "[Official Video]", "(Audio)", feat tags, etc.)
 * - Normalized artist (handling collaborations, feat tags, multiple artists)
 * - Duration tolerance of ±10 seconds when both durations are valid (> 0)
 */
object SongMatcher {

    private val TITLE_NOISE_REGEX = Regex(
        """(?i)[\(\[]\s*(?:official\s+)?(?:music\s+)?(?:video|audio|lyric\s+video|visualizer|mv|lyrics|hd|4k|remastered|hq|live|performance|extended\s+mix|clean)\s*[\)\]]"""
    )

    private val FEAT_REGEX = Regex(
        """(?i)[\(\[]\s*(?:feat|ft)\.?\s+[^\)\]]+[\)\]]"""
    )

    private val PUNCTUATION_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes a song title:
     * - Strips "(Official Audio)", "[MV]", etc.
     * - Strips "(feat. ...)" from title so featured artists don't hinder matching.
     * - Replaces punctuation with spaces and collapses whitespace.
     * - Lowercases.
     */
    fun normalizeTitle(title: String?): String {
        if (title.isNullOrBlank()) return ""
        val decoded = title.normalizeMetadataText() ?: title
        return decoded
            .replace(TITLE_NOISE_REGEX, " ")
            .replace(FEAT_REGEX, " ")
            .replace(PUNCTUATION_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Normalizes an artist name:
     * - Takes primary artist if multiple are separated by comma, slash, '&', or 'feat'/'ft'.
     * - Cleans punctuation and collapses whitespace.
     * - Lowercases.
     */
    fun normalizeArtist(artist: String?): String {
        if (artist.isNullOrBlank()) return ""
        val decoded = artist.normalizeMetadataText() ?: artist
        // Split on common artist delimiters
        val primary = decoded
            .split(Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|with|and|&)\s+|[,/&]"""))
            .firstOrNull()
            ?.trim() ?: decoded

        return primary
            .replace(PUNCTUATION_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Creates a composite lookup key for O(1) matching when titles and artists align closely.
     */
    fun createKey(title: String?, artist: String?): String {
        val nTitle = normalizeTitle(title)
        val nArtist = normalizeArtist(artist)
        return "$nTitle|$nArtist"
    }

    /**
     * Checks if a YouTube song and a local song match.
     *
     * Rules:
     * 1. Normalized titles must match (or one is an exact match / prefix of the other if long enough).
     * 2. Normalized artists must match or contain each other.
     * 3. Duration must be within 10 seconds if both durations are known (> 0).
     */
    fun isMatch(
        localTitle: String,
        localArtist: String,
        localDurationMs: Long,
        ytTitle: String,
        ytArtist: String,
        ytDurationMs: Long,
        durationToleranceMs: Long = 10_000L
    ): Boolean {
        val nLocalTitle = normalizeTitle(localTitle)
        val nYtTitle = normalizeTitle(ytTitle)

        if (nLocalTitle.isBlank() || nYtTitle.isBlank()) return false
        if (nLocalTitle != nYtTitle) {
            // Also allow match if one title is contained in the other and significant length (> 4 chars)
            val titleMatches = (nLocalTitle.length >= 5 && nYtTitle.length >= 5) &&
                (nLocalTitle == nYtTitle || nLocalTitle.startsWith(nYtTitle) || nYtTitle.startsWith(nLocalTitle))
            if (!titleMatches) return false
        }

        val nLocalArtist = normalizeArtist(localArtist)
        val nYtArtist = normalizeArtist(ytArtist)

        val artistMatches = when {
            nLocalArtist.isBlank() || nYtArtist.isBlank() -> true
            nLocalArtist == nYtArtist -> true
            nLocalArtist.contains(nYtArtist) || nYtArtist.contains(nLocalArtist) -> true
            else -> {
                // Check first token of artist name (e.g. "Taylor" from "Taylor Swift")
                val localFirst = nLocalArtist.split(" ").firstOrNull().orEmpty()
                val ytFirst = nYtArtist.split(" ").firstOrNull().orEmpty()
                localFirst.length >= 4 && localFirst == ytFirst
            }
        }

        if (!artistMatches) return false

        // Check duration tolerance (±10 seconds) if both durations are available
        if (localDurationMs > 0L && ytDurationMs > 0L) {
            if (abs(localDurationMs - ytDurationMs) > durationToleranceMs) {
                return false
            }
        }

        return true
    }
}
