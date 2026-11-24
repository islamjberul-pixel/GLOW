package com.example

import android.media.ExifInterface
import com.example.util.ScreenshotStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotStorageTest {

    @Test
    fun `supports png jpg jpeg and webp mime types`() {
        assertTrue(ScreenshotStorage.isSupportedProviderMime("image/png"))
        assertTrue(ScreenshotStorage.isSupportedProviderMime("image/jpeg"))
        assertTrue(ScreenshotStorage.isSupportedProviderMime("image/jpg"))
        assertTrue(ScreenshotStorage.isSupportedProviderMime("image/webp"))
        assertTrue(ScreenshotStorage.isSupportedProviderMime("application/octet-stream"))
        assertTrue(ScreenshotStorage.isSupportedProviderMime(null))

        assertTrue(ScreenshotStorage.isSupportedDecodedMime("image/png"))
        assertTrue(ScreenshotStorage.isSupportedDecodedMime("image/jpeg"))
        assertTrue(ScreenshotStorage.isSupportedDecodedMime("image/webp"))
    }

    @Test
    fun `rejects non-image provider mime types`() {
        assertFalse(ScreenshotStorage.isSupportedProviderMime("video/mp4"))
        assertFalse(ScreenshotStorage.isSupportedProviderMime("application/pdf"))
        assertFalse(ScreenshotStorage.isSupportedDecodedMime("application/pdf"))
    }

    @Test
    fun `computes safe power of two sample size for high resolution screenshots`() {
        assertEquals(1, ScreenshotStorage.computeSampleSize(1600, 720))
        assertEquals(1, ScreenshotStorage.computeSampleSize(1920, 1080))
        assertEquals(2, ScreenshotStorage.computeSampleSize(3840, 2160))
        assertEquals(4, ScreenshotStorage.computeSampleSize(6000, 4000))
    }

    @Test
    fun `swaps dimensions for rotated exif orientations`() {
        assertEquals(720 to 1600, ScreenshotStorage.applyOrientationToDimensions(1600, 720, ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(720 to 1600, ScreenshotStorage.applyOrientationToDimensions(1600, 720, ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(1600 to 720, ScreenshotStorage.applyOrientationToDimensions(1600, 720, ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(1600 to 720, ScreenshotStorage.applyOrientationToDimensions(1600, 720, ExifInterface.ORIENTATION_NORMAL))
    }
}
