package com.aicameraultra.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCapabilitiesTest {

    @Test
    fun filterSizes_keepsOnlyCommonRatiosSortedAndDistinct() {
        val raw = listOf(
            PhotoSize(4000, 3000), PhotoSize(1920, 1080), PhotoSize(1920, 1080),
            PhotoSize(640, 480), PhotoSize(3000, 3000), PhotoSize(3264, 2448)
        )
        val result = CameraCapabilities.filterSizes(raw)
        assertEquals(listOf(PhotoSize(4000, 3000), PhotoSize(3264, 2448), PhotoSize(1920, 1080)), result)
    }

    @Test
    fun filterSizes_respectsMax() {
        val raw = (1..20).map { PhotoSize(1280 + it * 4, (1280 + it * 4) * 3 / 4) }
        assertEquals(3, CameraCapabilities.filterSizes(raw, max = 3).size)
    }

    @Test
    fun labels_doNotClaimNative4kUnlessExactSize() {
        assertTrue(PhotoSize(3840, 2160).label.contains("4K"))
        assertFalse(PhotoSize(4000, 3000).label.contains("4K"))
        assertTrue(PhotoSize(1920, 1080).label.contains("Full HD"))
    }

    @Test
    fun ratioDetection() {
        assertTrue(PhotoSize(1920, 1080).isWide)
        assertFalse(PhotoSize(4000, 3000).isWide)
    }
}
