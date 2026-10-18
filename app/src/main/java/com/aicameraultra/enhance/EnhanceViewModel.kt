package com.aicameraultra.enhance

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicameraultra.imageprocessing.ExportSize
import com.aicameraultra.imageprocessing.ImageExporter
import com.aicameraultra.imageprocessing.ImageInspector
import com.aicameraultra.imageprocessing.SavedImageInfo
import com.aicameraultra.network.AiBackendClient
import com.aicameraultra.network.BackendException
import com.aicameraultra.network.BackendUrl
import com.aicameraultra.settings.AppSettings
import com.aicameraultra.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

enum class Phase { IDLE, PREPARING, PROCESSING, SAVING, DONE }

data class EnhanceUiState(
    val source: Uri? = null,
    val sourceInfo: SavedImageInfo? = null,
    val sourceBitmap: Bitmap? = null,
    val preset: EnhancePreset = EnhancePreset.FULL,
    val instructions: String = "",
    val phase: Phase = Phase.IDLE,
    val progress: String? = null,
    val result: Uri? = null,
    val resultInfo: SavedImageInfo? = null,
    val resultBitmap: Bitmap? = null,
    val sliderPos: Float = 0.5f,
    val settings: AppSettings = AppSettings(),
    val message: String? = null
) {
    val busy: Boolean get() = phase == Phase.PREPARING || phase == Phase.PROCESSING || phase == Phase.SAVING
}

/**
 * Edición con IA bajo demanda. Nunca se lanza automáticamente.
 * El original no se toca: el resultado se guarda como archivo nuevo.
 */
class EnhanceViewModel(private val application: Application) : AndroidViewModel(application) {

    private val resolver get() = application.contentResolver
    private val settingsRepo = SettingsRepository(application)
    private val _state = MutableStateFlow(EnhanceUiState(settings = settingsRepo.load()))
    val state: StateFlow<EnhanceUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setSource(uri: Uri) {
        job?.cancel()
        _state.value = EnhanceUiState(source = uri, settings = settingsRepo.load())
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { ImageInspector.readInfo(resolver, uri) }
            val bmp = withContext(Dispatchers.IO) { ImageInspector.decodeSampled(resolver, uri, 1280) }
            _state.update { if (it.source == uri) it.copy(sourceInfo = info, sourceBitmap = bmp) else it }
        }
    }

    fun setPreset(preset: EnhancePreset) = _state.update { it.copy(preset = preset) }
    fun setInstructions(text: String) = _state.update { it.copy(instructions = text.take(300)) }
    fun setSlider(position: Float) = _state.update { it.copy(sliderPos = position.coerceIn(0f, 1f)) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun cancel() {
        job?.cancel()
    }

    fun enhance() {
        if (job?.isActive == true) return
        val s = _state.value
        val source = s.source ?: return
        val baseUrl = BackendUrl.normalize(s.settings.backendUrl)
        if (baseUrl == null) {
            _state.update { it.copy(message = "Configura en Ajustes la dirección https del servidor de IA. La cámara funciona sin ella.") }
            return
        }
        if (s.preset == EnhancePreset.CUSTOM && s.instructions.isBlank()) {
            _state.update { it.copy(message = "Escribe una instrucción para la opción personalizada.") }
            return
        }
        val mode = settingsRepo.load().performanceMode
        job = viewModelScope.launch {
            var upload: File? = null
            try {
                setPhase(Phase.PREPARING, "Preparando copia temporal…")
                val file = ImageExporter.prepareUpload(application, source, mode.uploadLongSide, mode.uploadJpegQuality)
                upload = file

                setPhase(Phase.PROCESSING, "Procesando con IA (hasta ${mode.timeoutSeconds} s)…")
                val remote = AiBackendClient.enhance(baseUrl, file, s.preset, s.instructions.trim(), mode)

                setPhase(Phase.SAVING, "Guardando copia mejorada…")
                val uri = ImageExporter.saveBytes(application, remote.bytes, remote.mimeType, "_AI")
                val info = withContext(Dispatchers.IO) { ImageInspector.readInfo(resolver, uri) }
                val bmp = withContext(Dispatchers.IO) { ImageInspector.decodeSampled(resolver, uri, 1280) }
                _state.update {
                    it.copy(
                        phase = Phase.DONE,
                        progress = null,
                        result = uri,
                        resultInfo = info,
                        resultBitmap = bmp,
                        sliderPos = 0.5f,
                        message = if (info == null) "No se pudo verificar la copia guardada." else null
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = Phase.IDLE, progress = null, message = "Operación cancelada.") }
                throw e
            } catch (e: BackendException) {
                fail(e.message ?: "Error del servidor de IA.")
            } catch (e: IOException) {
                fail("Sin conexión o el servidor no responde. La cámara sigue funcionando sin IA.")
            } catch (e: OutOfMemoryError) {
                fail("Memoria insuficiente. Prueba con el modo Rápido en Ajustes.")
            } finally {
                upload?.delete()
            }
        }
    }

    fun exportCopy(size: ExportSize) {
        val s = _state.value
        val source = s.source ?: return
        viewModelScope.launch {
            try {
                val saved = ImageExporter.exportCopy(application, source, size, s.settings.jpegQuality)
                _state.update {
                    it.copy(message = "Copia guardada: ${saved.width}×${saved.height}. ${saved.note.orEmpty()}")
                }
            } catch (e: IOException) {
                _state.update { it.copy(message = e.message ?: "No se pudo exportar la copia.") }
            }
        }
    }

    private fun setPhase(phase: Phase, progress: String?) =
        _state.update { it.copy(phase = phase, progress = progress) }

    private fun fail(msg: String) =
        _state.update { it.copy(phase = Phase.IDLE, progress = null, message = msg) }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
