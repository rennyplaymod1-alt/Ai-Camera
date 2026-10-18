package com.aicameraultra.imageprocessing

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlin.math.max

data class SavedImageInfo(val width: Int, val height: Int, val sizeBytes: Long)

/** Lectura eficiente: solo cabeceras para dimensiones, y submuestreo para miniaturas. */
object ImageInspector {

    private fun orientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** Dimensiones reales (con la orientación EXIF aplicada) y tamaño del archivo guardado. */
    fun readInfo(resolver: ContentResolver, uri: Uri): SavedImageInfo? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var w = opts.outWidth
        var h = opts.outHeight
        if (w <= 0 || h <= 0) null else {
            when (orientation(resolver, uri)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> { val t = w; w = h; h = t }
            }
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            } ?: 0L
            if (size > 0) SavedImageInfo(w, h, size) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Decodifica una miniatura de ~maxDim px sin cargar la imagen completa en memoria. */
    fun decodeSampled(resolver: ContentResolver, uri: Uri, maxDim: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim && bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = max(1, sample) }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val degrees = when (orientation(resolver, uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (bmp != null && degrees != 0f) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees) }, true)
            if (rotated !== bmp) bmp.recycle()
            rotated
        } else bmp
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
