package com.aicameraultra.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageCapture
import com.aicameraultra.imageprocessing.SavedImageInfo
import com.aicameraultra.settings.AppSettings

data class CameraUiState(
    val useFront: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val hasFlash: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoom: Float = 1f,
    val maxZoom: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val timerSeconds: Int = 0,
    val countdown: Int = 0,
    val sizes: List<PhotoSize> = emptyList(),
    val sizesLoaded: Boolean = false,
    /** null = máxima resolución disponible (modo automático). */
    val selectedSize: PhotoSize? = null,
    val isCapturing: Boolean = false,
    val lastPhotoUri: Uri? = null,
    val lastThumb: Bitmap? = null,
    val lastInfo: SavedImageInfo? = null,
    val message: String? = null,
    val settings: AppSettings = AppSettings()
) {
    val exposureSupported: Boolean get() = exposureMax > exposureMin
    val zoomSupported: Boolean get() = maxZoom > minZoom + 0.01f
}
