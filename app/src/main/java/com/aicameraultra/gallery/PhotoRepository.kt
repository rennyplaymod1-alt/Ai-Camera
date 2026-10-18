package com.aicameraultra.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guardado y consulta de fotos en la galería (MediaStore). Los originales nunca se modifican. */
object PhotoRepository {
    const val FILE_PREFIX = "AICU_"
    private const val ALBUM = "Pictures/AICameraUltra"

    fun outputOptions(resolver: ContentResolver): ImageCapture.OutputFileOptions {
        val name = FILE_PREFIX + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, ALBUM)
            }
        }
        return ImageCapture.OutputFileOptions.Builder(
            resolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
    }

    /** Última foto tomada por la app (se identifica por el prefijo del nombre). */
    fun latestPhotoUri(resolver: ContentResolver): Uri? = try {
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$FILE_PREFIX%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            if (c.moveToFirst())
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
            else null
        }
    } catch (e: Exception) {
        null
    }
}
