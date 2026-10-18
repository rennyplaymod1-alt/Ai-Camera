package com.aicameraultra.camera

import android.app.Application
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicameraultra.gallery.PhotoRepository
import com.aicameraultra.imageprocessing.ImageInspector
import com.aicameraultra.settings.AppSettings
import com.aicameraultra.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Usa el contexto de aplicación (no de Activity) para evitar fugas de memoria. */
class CameraViewModel(app: Application) : AndroidViewModel(app) {

    private val resolver get() = getApplication<Application>().contentResolver
    private val settingsRepo = SettingsRepository(app)
    private val _state = MutableStateFlow(CameraUiState(settings = settingsRepo.load()))
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private var timerJob: Job? = null

    init {
        loadSizes(front = false)
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { PhotoRepository.latestPhotoUri(resolver) }
            if (uri != null) {
                val thumb = withContext(Dispatchers.IO) { ImageInspector.decodeSampled(resolver, uri, 192) }
                _state.update { it.copy(lastPhotoUri = uri, lastThumb = thumb) }
            }
        }
    }

    private fun loadSizes(front: Boolean) {
        viewModelScope.launch {
            val sizes = withContext(Dispatchers.Default) {
                CameraCapabilities.supportedJpegSizes(getApplication(), front)
            }
            _state.update { it.copy(sizes = sizes, sizesLoaded = true) }
        }
    }

    fun toggleCamera() {
        cancelTimer()
        _state.update {
            it.copy(useFront = !it.useFront, selectedSize = null, sizesLoaded = false,
                flashMode = ImageCapture.FLASH_MODE_OFF)
        }
        loadSizes(front = _state.value.useFront)
    }

    fun cycleFlash() = _state.update {
        val next = when (it.flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }
        it.copy(flashMode = next)
    }

    fun cycleTimer() = _state.update {
        it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 })
    }

    fun setZoom(value: Float) = _state.update { it.copy(zoomRatio = value.coerceIn(it.minZoom, it.maxZoom)) }
    fun setExposure(index: Int) = _state.update { it.copy(exposureIndex = index.coerceIn(it.exposureMin, it.exposureMax)) }
    fun selectSize(size: PhotoSize?) = _state.update { it.copy(selectedSize = size) }

    fun onCameraReady(hasFlash: Boolean, minZoom: Float, maxZoom: Float, expMin: Int, expMax: Int) {
        _state.update {
            it.copy(
                hasFlash = hasFlash,
                flashMode = if (hasFlash) it.flashMode else ImageCapture.FLASH_MODE_OFF,
                minZoom = minZoom, maxZoom = maxZoom,
                zoomRatio = 1f.coerceIn(minZoom, maxZoom),
                exposureMin = expMin, exposureMax = expMax, exposureIndex = 0
            )
        }
    }

    /** Pulsar el obturador: inmediato o con cuenta atrás. Pulsar durante la cuenta atrás la cancela. */
    fun shutter(takePicture: () -> Unit) {
        val s = _state.value
        if (s.countdown > 0) { cancelTimer(); return }
        if (s.isCapturing) return
        if (s.timerSeconds == 0) {
            _state.update { it.copy(isCapturing = true) }
            takePicture()
        } else {
            timerJob = viewModelScope.launch {
                for (i in s.timerSeconds downTo 1) {
                    _state.update { it.copy(countdown = i) }
                    delay(1000)
                }
                _state.update { it.copy(countdown = 0, isCapturing = true) }
                takePicture()
            }
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(countdown = 0) }
    }

    fun onPhotoSaved(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) { onError("No se recibió la ubicación de la foto guardada."); return@launch }
            val (info, thumb) = withContext(Dispatchers.IO) {
                ImageInspector.readInfo(resolver, uri) to ImageInspector.decodeSampled(resolver, uri, 192)
            }
            _state.update {
                it.copy(
                    isCapturing = false,
                    lastPhotoUri = if (info != null) uri else it.lastPhotoUri,
                    lastInfo = info,
                    lastThumb = if (info != null) thumb ?: it.lastThumb else it.lastThumb,
                    message = if (info == null) "No se pudo verificar el archivo guardado." else null
                )
            }
        }
    }

    fun onError(message: String) = _state.update { it.copy(isCapturing = false, message = message) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun updateSettings(settings: AppSettings) {
        settingsRepo.save(settings)
        _state.update { it.copy(settings = settings) }
    }
}
