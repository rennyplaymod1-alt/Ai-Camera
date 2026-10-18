package com.aicameraultra.imageprocessing

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportSizeTest {

    @Test
    fun fullHd_fitsLandscapeSource() {
        assertEquals(1920 to 1080, ExportSize.FULL_HD.fit(4000, 2250))
    }

    @Test
    fun uhd4k_scalesDownKeepingAspect() {
        assertEquals(2880 to 2160, ExportSize.UHD_4K.fit(4000, 3000))
    }

    @Test
    fun neverUpscales() {
        assertEquals(1000 to 750, ExportSize.UHD_4K.fit(1000, 750))
    }

    @Test
    fun portraitUsesSwappedBox() {
        assertEquals(1080 to 1440, ExportSize.FULL_HD.fit(3000, 4000))
    }

    @Test
    fun originalKeepsSize() {
        assertEquals(4032 to 3024, ExportSize.ORIGINAL.fit(4032, 3024))
    }
}
