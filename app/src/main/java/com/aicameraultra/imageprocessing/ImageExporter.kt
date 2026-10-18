package com.aicameraultra.imageprocessing

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Exportación y guardado. Los originales NUNCA se modifican: las copias son archivos nuevos.
 * Solo se decodifica una imagen grande a la vez (Mutex) para no agotar la memoria.
 */
object ImageExporter {

    data class Saved(val uri: Uri, val width: Int, val height: Int, val note: String?)

    private const val ALBUM = "Pictures/AICameraUltra"
    private val mutex = Mutex()

    suspend fun exportCopy(context: Context, source: Uri, size: ExportSize, quality: Int): Saved =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val resolver = context.contentResolver
                val info = ImageInspector.readInfo(resolver, source)
                    ?: throw IOException("No se pudo leer la foto original.")
                val (w, h) = size.fit(info.width, info.height)

                if (size == ExportSize.ORIGINAL) {
                    return@withLock copyOriginal(resolver, source, info.width, info.height)
                }
                try {
                    val bmp = decodeOriented(resolver, source, max(w, h), Bitmap.Config.ARGB_8888)
                        ?: throw IOException("Formato de imagen no compatible.")
                    val out = if (bmp.width == w && bmp.height == h) bmp
                    else Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
                    try {
                        val uri = writeJpeg(resolver, out, newName(size.suffix, "jpg"), quality)
                        val note = when {
                            w == info.width && h == info.height ->
                                "La foto original ya era menor o igual que ${size.shortLabel}; no se amplió."
                            size == ExportSize.UHD_4K ->
                                "Copia escalada desde ${info.width}×${info.height}. No es una captura 4K nativa."
                            else -> "Copia escalada desde ${info.width}×${info.height}."
                        }
                        Saved(uri, w, h, note)
                    } finally {
                        out.recycle()
                    }
                } catch (e: OutOfMemoryError) {
                    throw IOException("Memoria insuficiente para exportar esta resolución.")
                }
            }
        }

    /** Copia temporal reducida solo para enviar a la IA. Se borra después de usarla. */
    suspend fun prepareUpload(context: Context, source: Uri, longSide: Int, quality: Int): File =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val info = ImageInspector.readInfo(resolver, source)
                ?: throw IOException("No se pudo leer la foto original.")
            val (w, h) = fitInBox(info.width, info.height, longSide, longSide)
            val bmp = decodeOriented(resolver, source, max(w, h), Bitmap.Config.RGB_565)
                ?: throw IOException("No se pudo preparar la foto.")
            val out = if (bmp.width == w && bmp.height == h) bmp
            else Bitmap.createScaledBitmap(bmp, w, h, true).also { bmp.recycle() }
            val file = File(context.cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { stream ->
                    if (!out.compress(Bitmap.CompressFormat.JPEG, quality, stream))
                        throw IOException("No se pudo codificar la copia temporal.")
                }
            } catch (e: IOException) {
                file.delete()
                throw e
            } finally {
                out.recycle()
            }
            file
        }

    /** Guarda bytes devueltos por el servidor (ya codificados). */
    suspend fun saveBytes(context: Context, bytes: ByteArray, mime: String, suffix: String): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val uri = insert(resolver, newName(suffix, ext), mime)
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("No se pudo abrir el archivo de salida.")
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw IOException("No se pudo guardar la copia mejorada.")
            }
            uri
        }

    fun newName(suffix: String, ext: String): String =
        "AICU_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + suffix + "." + ext

    private fun copyOriginal(resolver: ContentResolver, source: Uri, w: Int, h: Int): Saved {
        val mime = resolver.getType(source) ?: "image/jpeg"
        val ext = if (mime == "image/png") "png" else "jpg"
        val uri = insert(resolver, newName(ExportSize.ORIGINAL.suffix, ext), mime)
        try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(uri)?.use { output -> input.copyTo(output) }
            } ?: throw IOException("No se pudo leer la original.")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw IOException("No se pudo copiar la foto original.")
        }
        return Saved(uri, w, h, "Copia idéntica a la original (sin re-codificar, conserva metadatos).")
    }

    private fun writeJpeg(resolver: ContentResolver, bmp: Bitmap, name: String, quality: Int): Uri {
        val uri = insert(resolver, name, "image/jpeg")
        try {
            val ok = resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) } ?: false
            if (!ok) throw IOException("No se pudo codificar la imagen.")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw if (e is IOException) e else IOException("No se pudo guardar la imagen.")
        }
        return uri
    }

    private fun insert(resolver: ContentResolver, name: String, mime: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM)
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("No se pudo crear el archivo en la galería.")
    }

    /** Decodifica submuestreando (sin cargar la imagen completa si no hace falta) y aplica la orientación EXIF. */
    private fun decodeOriented(resolver: ContentResolver, uri: Uri, reqLong: Int, config: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longSrc = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSrc / (sample * 2) >= reqLong) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = config }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = when (exifOrientation(resolver, uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return raw
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
        raw.recycle()
        return rotated
    }

    private fun exifOrientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
}
