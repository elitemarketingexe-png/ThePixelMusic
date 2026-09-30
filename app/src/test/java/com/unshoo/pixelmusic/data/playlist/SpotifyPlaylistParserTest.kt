package com.unshoo.pixelmusic.data.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyPlaylistParserTest {

    @Test
    fun extractPlaylistId_fromStandardUrl_returnsId() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc123xyz"
        val id = SpotifyPlaylistParser.extractPlaylistId(url)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", id)
    }

    @Test
    fun extractPlaylistId_fromLocalizedUrl_returnsId() {
        val url = "https://open.spotify.com/intl-es/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val id = SpotifyPlaylistParser.extractPlaylistId(url)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", id)
    }

    @Test
    fun extractPlaylistId_fromUri_returnsId() {
        val uri = "spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"
        val id = SpotifyPlaylistParser.extractPlaylistId(uri)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", id)
    }

    @Test
    fun extractPlaylistId_fromDirectId_returnsId() {
        val rawId = "37i9dQZF1DXcBWIGoYBM5M"
        val id = SpotifyPlaylistParser.extractPlaylistId(rawId)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", id)
    }

    @Test
    fun extractPlaylistId_fromInvalidInput_returnsNull() {
        val invalid = "https://invalid-site.com/track/123"
        val id = SpotifyPlaylistParser.extractPlaylistId(invalid)
        assertNull(id)
    }
}
