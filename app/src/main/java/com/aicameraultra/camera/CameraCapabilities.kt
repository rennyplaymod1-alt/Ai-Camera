package com.aicameraultra.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.util.Locale
import kotlin.math.abs

/** Tamaño de foto (orientación del sensor, ancho >= alto). */
data class PhotoSize(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height
    val megapixels: Double get() = pixels / 1_000_000.0
    val isWide: Boolean get() = isWideRatio(width, height)

    val label: String
        get() = when {
            width == 1920 && height == 1080 -> "Full HD · 1920×1080"
            width == 3840 && height == 2160 -> "4K nativo · 3840×2160"
            else -> "$width×$height · ${String.format(Locale.US, "%.1f", megapixels)} MP"
        }

    val shortLabel: String
        get() = when {
            width == 1920 && height == 1080 -> "FHD"
            width == 3840 && height == 2160 -> "4K"
            else -> "${String.format(Locale.US, "%.0f", megapixels)} MP"
        }
}

fun isWideRatio(w: Int, h: Int): Boolean = abs(w.toFloat() / h - 16f / 9f) < 0.03f
fun isStandardRatio(w: Int, h: Int): Boolean = abs(w.toFloat() / h - 4f / 3f) < 0.03f

object CameraCapabilities {

    /**
     * Consulta a Camera2 los tamaños JPEG que la cámara declara.
     * Lo declarado por el sensor puede diferir de lo que CameraX termine eligiendo;
     * la resolución final real se muestra tras guardar cada foto.
     */
    fun supportedJpegSizes(context: Context, front: Boolean): List<PhotoSize> = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT
        else CameraCharacteristics.LENS_FACING_BACK
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
        }
        val map = id?.let {
            manager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        }
        val raw = map?.getOutputSizes(ImageFormat.JPEG)
            ?.map { PhotoSize(it.width, it.height) }
            .orEmpty()
        filterSizes(raw)
    } catch (e: Exception) {
        emptyList()
    }

    /** Función pura (testeable): solo 4:3 y 16:9, >= 720p, sin duplicados, de mayor a menor. */
    fun filterSizes(raw: List<PhotoSize>, max: Int = 8): List<PhotoSize> = raw
        .filter { (isStandardRatio(it.width, it.height) || isWideRatio(it.width, it.height)) }
        .filter { it.pixels >= 1280L * 720L }
        .distinct()
        .sortedByDescending { it.pixels }
        .take(max)
}
