package com.aicameraultra.imageprocessing

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Tamaños de exportación. Las copias 4K se identifican como escaladas cuando la captura
 * original no es 4K nativa. Nunca se amplía una imagen.
 */
enum class ExportSize(val label: String, val shortLabel: String, val boxLong: Int, val boxShort: Int, val suffix: String) {
    ORIGINAL("Original (sin re-codificar)", "Original", 0, 0, "_copia"),
    FULL_HD("Full HD 1920×1080", "FHD", 1920, 1080, "_FHD"),
    UHD_4K("4K 3840×2160 (escalado si no es nativo)", "4K", 3840, 2160, "_4K");

    fun fit(srcW: Int, srcH: Int): Pair<Int, Int> =
        if (this == ORIGINAL) srcW to srcH else fitInBox(srcW, srcH, boxLong, boxShort)
}

/** Ajusta el tamaño dentro de una caja manteniendo proporción. Sin ampliar. */
fun fitInBox(srcW: Int, srcH: Int, boxLong: Int, boxShort: Int): Pair<Int, Int> {
    val landscape = srcW >= srcH
    val bw = if (landscape) boxLong else boxShort
    val bh = if (landscape) boxShort else boxLong
    val scale = min(min(bw.toDouble() / srcW, bh.toDouble() / srcH), 1.0)
    return max(1, (srcW * scale).roundToInt()) to max(1, (srcH * scale).roundToInt())
}
