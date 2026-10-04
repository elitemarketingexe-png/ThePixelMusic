package com.unshoo.pixelmusic.data.update

import com.unshoo.pixelmusic.data.model.update.AppReleaseAsset
import com.unshoo.pixelmusic.data.model.update.AppReleaseInfo
import com.unshoo.pixelmusic.data.model.update.SemVerComparator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppUpdateTest {

    @Test
    fun testSemVerComparator_equalVersions() {
        assertEquals(0, SemVerComparator.compare("v1.6.09", "1.6.09"))
        assertEquals(0, SemVerComparator.compare("1.6.09", "1.6.9"))
        assertEquals(0, SemVerComparator.compare("v2.0.0", "2.0.0"))
        assertFalse(SemVerComparator.isNewer("v1.6.09", "1.6.09"))
    }

    @Test
    fun testSemVerComparator_newerPatch() {
        assertTrue(SemVerComparator.isNewer("v1.6.10", "1.6.09"))
        assertTrue(SemVerComparator.isNewer("1.6.10", "1.6.09"))
        assertFalse(SemVerComparator.isNewer("1.6.09", "1.6.10"))
    }

    @Test
    fun testSemVerComparator_newerMinorAndMajor() {
        assertTrue(SemVerComparator.isNewer("v1.7.0", "1.6.09"))
        assertTrue(SemVerComparator.isNewer("2.0.0", "1.6.09"))
        assertFalse(SemVerComparator.isNewer("1.5.99", "1.6.09"))
    }

    @Test
    fun testSemVerComparator_prerelease() {
        // Official release is newer than pre-release with same base
        assertTrue(SemVerComparator.isNewer("1.6.10", "1.6.10-beta1"))
        // Beta 2 is newer than Beta 1
        assertTrue(SemVerComparator.isNewer("1.6.10-beta2", "1.6.10-beta1"))
    }

    @Test
    fun testReleaseAssetExtraction() {
        val assets = listOf(
            AppReleaseAsset(
                name = "app-arm64-v8a-release.apk",
                downloadUrl = "https://github.com/ianshulyadav/PixelMusicApp/releases/download/v1.6.09/app-arm64-v8a-release.apk",
                sizeBytes = 44089934L
            ),
            AppReleaseAsset(
                name = "app-universal-release.apk",
                downloadUrl = "https://github.com/ianshulyadav/PixelMusicApp/releases/download/v1.6.09/app-universal-release.apk",
                sizeBytes = 74243892L
            )
        )

        val release = AppReleaseInfo(
            tagName = "v1.6.09",
            name = "PixelMusic v1.6.09",
            body = "Release changelog",
            htmlUrl = "https://github.com/ianshulyadav/PixelMusicApp/releases/tag/v1.6.09",
            assets = assets
        )

        assertEquals("v1.6.09", release.tagName)
        assertEquals(2, release.assets.size)
        assertEquals(44089934L, release.assets[0].sizeBytes)
    }
}
