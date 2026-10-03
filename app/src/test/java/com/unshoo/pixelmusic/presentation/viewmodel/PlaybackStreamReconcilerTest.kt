package com.unshoo.pixelmusic.presentation.viewmodel

import com.unshoo.pixelmusic.utils.AudioFamily
import com.unshoo.pixelmusic.utils.AudioMetaUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamReconcilerTest {

    private val id = "youtube_abc123"

    private fun opus(bitrate: Int? = 160_000) = ConfirmedAudioFormat(
        sampleMimeType = "audio/opus", containerMimeType = "audio/webm",
        bitrate = bitrate, sampleRate = 48_000, channelCount = 2, bitDepth = null,
    )

    private fun flac(sampleRate: Int? = 96_000, bitDepth: Int? = 24, bitrate: Int? = null) = ConfirmedAudioFormat(
        sampleMimeType = "audio/flac", containerMimeType = null,
        bitrate = bitrate, sampleRate = sampleRate, channelCount = 2, bitDepth = bitDepth,
    )

    private fun hiResOffer(source: String = "TIDAL") = OfferedStream(
        mimeType = "audio/flac", bitrate = 2_304_000, sampleRate = 96_000,
        bitDepth = 24, sourceName = source, labelSaysHiRes = true,
    )

    @Test
    fun `offer alone never makes an opus stream lossless`() {
        // Player still on the old OPUS stream while a Hi-Res stream has been resolved.
        val result = PlaybackStreamReconciler.reconcile(id, opus(), hiResOffer(), previous = null)

        assertNull(result.formatTag)
        assertNull(result.bitDepth)
        assertEquals(AudioFamily.OPUS, AudioFamily.of(result.mimeType))
        assertFalse(result.isLossless)
        assertTrue(result.isConfirmed)
    }

    @Test
    fun `stale hi-res state never leaks onto an opus stream`() {
        val staleHiRes = PlaybackAudioMetadata(
            mediaId = id, mimeType = "audio/flac", bitrate = 2_304_000, sampleRate = 96_000,
            bitDepth = 24, formatTag = "HI-RES LOSSLESS", sourceName = "TIDAL", isConfirmed = true,
        )
        val result = PlaybackStreamReconciler.reconcile(id, opus(), offered = null, previous = staleHiRes)

        assertNull(result.formatTag)
        assertNull(result.bitDepth)
        assertEquals(48_000, result.sampleRate)
        assertEquals("YouTube", result.sourceName) // not "TIDAL"
        val label = AudioMetaUtils.formatAudioMetaLabel(
            result.mimeType, result.bitrate, result.sampleRate, result.bitDepth, result.formatTag,
        )
        assertFalse(label.orEmpty().contains("HI-RES"))
        assertFalse(label.orEmpty().contains("LOSSLESS"))
    }

    @Test
    fun `upgrade confirmed by the player switches the label to hi-res lossless`() {
        val before = PlaybackStreamReconciler.reconcile(id, opus(), hiResOffer(), previous = null)
        val after = PlaybackStreamReconciler.reconcile(id, flac(), hiResOffer(), previous = before)

        assertEquals("HI-RES LOSSLESS", after.formatTag)
        assertEquals(24, after.bitDepth)
        assertEquals(96_000, after.sampleRate)
        assertEquals("TIDAL", after.sourceName)
        assertEquals(2_304_000, after.bitrate) // measured by the resolver, same codec family
        assertEquals(
            "HI-RES LOSSLESS",
            AudioMetaUtils.formatAudioMetaLabel(
                after.mimeType, after.bitrate, after.sampleRate, after.bitDepth, after.formatTag,
            ),
        )
    }

    @Test
    fun `plain 16 bit flac is lossless but not hi-res`() {
        val result = PlaybackStreamReconciler.reconcile(
            id, flac(sampleRate = 44_100, bitDepth = 16), offered = null, previous = null,
        )
        assertEquals("LOSSLESS", result.formatTag)
    }

    @Test
    fun `lossless fields are not invented when nothing reports them`() {
        val bare = ConfirmedAudioFormat("audio/flac", null, null, null, null, null)
        val result = PlaybackStreamReconciler.reconcile(id, bare, offered = null, previous = null)

        assertEquals("LOSSLESS", result.formatTag)
        assertNull(result.bitrate)
        assertNull(result.sampleRate)
        assertNull(result.bitDepth)
    }

    @Test
    fun `offer of a different codec family is ignored`() {
        // Offer says AAC, player decodes FLAC: nothing from the offer may be used.
        val aacOffer = OfferedStream("audio/mp4", 320_000, 44_100, null, "JioSaavn", false)
        val result = PlaybackStreamReconciler.reconcile(id, flac(), aacOffer, previous = null)

        assertEquals(AudioFamily.FLAC, AudioFamily.of(result.mimeType))
        assertNotNull(result.formatTag)
        assertEquals("YouTube", result.sourceName)
    }

    @Test
    fun `dash chunk bitrate is replaced by the codec default`() {
        val result = PlaybackStreamReconciler.reconcile(id, opus(bitrate = 51_000), null, null)
        assertEquals(160_000, result.bitrate)
    }

    @Test
    fun `falling back from flac to opus drops the lossless identity`() {
        val onFlac = PlaybackStreamReconciler.reconcile(id, flac(), hiResOffer(), previous = null)
        val backOnOpus = PlaybackStreamReconciler.reconcile(id, opus(), hiResOffer(), previous = onFlac)

        assertNull(backOnOpus.formatTag)
        assertNull(backOnOpus.bitDepth)
        assertEquals("YouTube", backOnOpus.sourceName)
    }

    @Test
    fun `label formatter refuses hi-res for a lossy codec even with a stale tag`() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/webm; codecs=\"opus\"", bitrate = 160_000, sampleRate = 96_000,
            bitDepth = 24, formatTag = "HI-RES LOSSLESS",
        )
        assertFalse(label.orEmpty().contains("HI-RES"))
        assertFalse(label.orEmpty().contains("LOSSLESS"))
    }

    @Test
    fun `audio family classification`() {
        assertEquals(AudioFamily.OPUS, AudioFamily.of("audio/webm; codecs=\"opus\""))
        assertEquals(AudioFamily.VORBIS, AudioFamily.of("audio/webm; codecs=\"vorbis\""))
        assertEquals(AudioFamily.ALAC, AudioFamily.of("audio/mp4; codecs=\"alac\""))
        assertEquals(AudioFamily.AAC, AudioFamily.of("audio/mp4a-latm"))
        assertEquals(AudioFamily.FLAC, AudioFamily.of("audio/flac"))
        assertEquals(AudioFamily.PCM, AudioFamily.of("audio/raw"))
        assertEquals(AudioFamily.MP3, AudioFamily.of("audio/mpeg"))
        assertEquals(AudioFamily.OTHER, AudioFamily.of(null))
    }
}
