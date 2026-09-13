package com.unshoo.pixelmusic.data.feed

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.utils.DEFAULT_WORD_DELIMITERS
import com.unshoo.pixelmusic.utils.splitArtistsByDelimiters

/**
 * Clean wrapper for multi-artist splitting in feed and recommendations.
 * Reuses the app's standard [splitArtistsByDelimiters] and strips dirty punctuation.
 */
object ArtistHelper {
    fun splitArtists(rawArtist: String?): List<String> =
        splitArtists(rawArtist, UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS, DEFAULT_WORD_DELIMITERS)

    fun splitArtists(
        rawArtist: String?,
        delimiters: List<String>,
        wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
    ): List<String> {
        if (rawArtist.isNullOrBlank()) return emptyList()
        return rawArtist.splitArtistsByDelimiters(delimiters, wordDelimiters)
            .map { it.trim(',', '&', '/', ';', '+', '•', '·', '.', '-').trim() }
            .filter { it.any { ch -> ch.isLetterOrDigit() } }
    }

    fun primaryArtist(rawArtist: String?): String =
        splitArtists(rawArtist).firstOrNull().orEmpty()

    fun primaryArtist(
        rawArtist: String?,
        delimiters: List<String>,
        wordDelimiters: List<String> = DEFAULT_WORD_DELIMITERS
    ): String = splitArtists(rawArtist, delimiters, wordDelimiters).firstOrNull().orEmpty()
}


