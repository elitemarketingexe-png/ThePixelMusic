package com.unshoo.pixelmusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMetaUtilsTest {

    @Test
    fun mimeTypeToFormat_mapsM4aVariants() {
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/x-m4a"))
        assertEquals("m4a", AudioMetaUtils.mimeTypeToFormat("audio/mp4a-latm"))
    }

    @Test
    fun mimeTypeToFormat_mapsSamsungFormats() {
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/amr"))
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/amr-wb"))
        assertEquals("amr", AudioMetaUtils.mimeTypeToFormat("audio/3gpp"))
        assertEquals("evrc", AudioMetaUtils.mimeTypeToFormat("audio/evrc"))
        assertEquals("evrc", AudioMetaUtils.mimeTypeToFormat("audio/x-evrc"))
        assertEquals("qcelp", AudioMetaUtils.mimeTypeToFormat("audio/qcelp"))
        assertEquals("qcelp", AudioMetaUtils.mimeTypeToFormat("audio/x-qcelp"))
        assertEquals("ima", AudioMetaUtils.mimeTypeToFormat("audio/x-ima-adpcm"))
        assertEquals("ima", AudioMetaUtils.mimeTypeToFormat("audio/ima-adpcm"))
    }

    @Test
    fun mimeTypeToFormat_mapsUniversalFormats() {
        assertEquals("aiff", AudioMetaUtils.mimeTypeToFormat("audio/x-aiff"))
        assertEquals("ac3", AudioMetaUtils.mimeTypeToFormat("audio/ac3"))
        assertEquals("dts", AudioMetaUtils.mimeTypeToFormat("audio/vnd.dts"))
        assertEquals("mp3", AudioMetaUtils.mimeTypeToFormat("audio/mpeg"))
        assertEquals("flac", AudioMetaUtils.mimeTypeToFormat("audio/flac"))
        assertEquals("wav", AudioMetaUtils.mimeTypeToFormat("audio/wav"))
        assertEquals("ogg", AudioMetaUtils.mimeTypeToFormat("audio/ogg"))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("audio/opus"))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("audio/webm; codecs=\"opus\""))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("audio/webm"))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("video/webm"))
        assertEquals("opus", AudioMetaUtils.mimeTypeToFormat("audio/x-opus"))
    }

    @Test
    fun mimeTypeToFormat_returnsDashForNullOrEmpty() {
        assertEquals("-", AudioMetaUtils.mimeTypeToFormat(null))
        assertEquals("-", AudioMetaUtils.mimeTypeToFormat(""))
        assertEquals("-", AudioMetaUtils.mimeTypeToFormat("   "))
    }

    @Test
    fun formatAudioMetaLabel_displaysOpusInsteadOfWebm() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 160_000,
            sampleRate = 48_000
        )
        assertEquals("48 kHz • 160 kbps • OPUS", label)
    }

    @Test
    fun formatAudioMetaLabel_displaysLosslessForFlacAndLosslessTag() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/flac",
            bitrate = 900_000,
            sampleRate = 44_100,
            formatTag = "LOSSLESS"
        )
        assertEquals("LOSSLESS • FLAC", label)
    }

    @Test
    fun formatAudioMetaLabel_displaysLosslessFlacWhenContainerIsM4a() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/mp4",
            bitrate = 900_000,
            sampleRate = 44_100,
            formatTag = "LOSSLESS"
        )
        assertEquals("LOSSLESS • FLAC", label)
    }

    @Test
    fun formatAudioMetaLabel_displaysHiResLossless() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/flac",
            bitrate = 2_400_000,
            sampleRate = 96_000,
            bitDepth = 24,
            formatTag = "HI-RES LOSSLESS"
        )
        assertEquals("HI-RES LOSSLESS", label)
    }

    @Test
    fun formatAudioMetaLabel_displaysHiResLosslessWhenHigherKhz() {
        val label = AudioMetaUtils.formatAudioMetaLabel(
            mimeType = "audio/mp4",
            bitrate = 2_400_000,
            sampleRate = 96_000,
            formatTag = "LOSSLESS"
        )
        assertEquals("HI-RES LOSSLESS", label)
    }

    @Test
    fun formatShareCardAudioTag_displaysOpusInsteadOfFullString() {
        val tag = AudioMetaUtils.formatShareCardAudioTag(
            mimeType = "audio/webm; codecs=\"opus\"",
            sampleRate = 48_000
        )
        assertEquals("OPUS", tag)
    }

    @Test
    fun formatShareCardAudioTag_displaysLosslessForFlac() {
        val tag = AudioMetaUtils.formatShareCardAudioTag(
            mimeType = "audio/flac",
            formatTag = "LOSSLESS"
        )
        assertEquals("LOSSLESS", tag)
    }

    @Test
    fun formatShareCardAudioTag_displaysHiResLossless() {
        val tag = AudioMetaUtils.formatShareCardAudioTag(
            mimeType = "audio/flac",
            sampleRate = 96_000,
            bitDepth = 24
        )
        assertEquals("HI-RES LOSSLESS", tag)
    }
}
