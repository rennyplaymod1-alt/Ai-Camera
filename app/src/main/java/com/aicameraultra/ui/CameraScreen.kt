package com.aicameraultra.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicameraultra.camera.CameraViewModel
import com.aicameraultra.camera.PhotoSize
import com.aicameraultra.gallery.PhotoRepository
import com.aicameraultra.performance.PerformanceMode
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({
        try { cont.resume(future.get()) } catch (e: Exception) { cont.resumeWithException(e) }
    }, ContextCompat.getMainExecutor(this))
}

@Composable
fun CameraScreen(vm: CameraViewModel, onOpenSettings: () -> Unit, onOpenPhoto: (Uri) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val previewView = remember { PreviewView(context) }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // --- Enlace de CameraX: se repite solo cuando cambia algo que exige reconfigurar la cámara.
    LaunchedEffect(state.useFront, state.selectedSize, state.sizesLoaded, state.settings.jpegQuality, state.settings.performanceMode) {
        if (!state.sizesLoaded) return@LaunchedEffect
        try {
            val provider = context.awaitCameraProvider()
            providerHolder[0] = provider
            provider.unbindAll()

            val target = state.selectedSize ?: state.sizes.firstOrNull()
            val wide = target?.isWide ?: false
            val aspect = if (wide) AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
            else AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

            // La resolución de la VISTA PREVIA es independiente de la de la FOTO final.
            val previewSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspect)
                .setResolutionStrategy(
                    ResolutionStrategy(
                        if (wide) Size(1280, 720) else Size(1280, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                ).build()

            val captureSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(aspect)
                .setResolutionStrategy(
                    state.selectedSize?.let {
                        ResolutionStrategy(
                            Size(it.width, it.height),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    } ?: ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
                ).build()

            val preview = Preview.Builder().setResolutionSelector(previewSelector).build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val capture = ImageCapture.Builder()
                .setResolutionSelector(captureSelector)
                .setCaptureMode(
                    if (state.settings.performanceMode == PerformanceMode.MAX_QUALITY)
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    else ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                )
                .setJpegQuality(state.settings.jpegQuality)
                .build()

            val selector = if (state.useFront) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

            val cam = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            camera = cam
            imageCapture = capture

            val zoom = cam.cameraInfo.zoomState.value
            val exp = cam.cameraInfo.exposureState
            vm.onCameraReady(
                hasFlash = cam.cameraInfo.hasFlashUnit(),
                minZoom = zoom?.minZoomRatio ?: 1f,
                maxZoom = zoom?.maxZoomRatio ?: 1f,
                expMin = if (exp.isExposureCompensationSupported) exp.exposureCompensationRange.lower else 0,
                expMax = if (exp.isExposureCompensationSupported) exp.exposureCompensationRange.upper else 0
            )
        } catch (e: Exception) {
            camera = null
            imageCapture = null
            vm.onError("No se pudo abrir la cámara (¿está en uso por otra app?): ${e.message ?: "error desconocido"}")
        }
    }

    // Parámetros en vivo (no requieren reenlazar).
    LaunchedEffect(state.flashMode, imageCapture) { imageCapture?.flashMode = state.flashMode }
    LaunchedEffect(state.zoomRatio, camera) { camera?.cameraControl?.setZoomRatio(state.zoomRatio) }
    LaunchedEffect(state.exposureIndex, camera) {
        if (state.exposureSupported) camera?.cameraControl?.setExposureCompensationIndex(state.exposureIndex)
    }

    // Liberar la cámara al salir de esta pantalla.
    DisposableEffect(Unit) { onDispose { providerHolder[0]?.unbindAll() } }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    fun takePhoto() {
        val capture = imageCapture
        if (capture == null) { vm.onError("La cámara aún no está lista."); return }
        capture.takePicture(
            PhotoRepository.outputOptions(context.contentResolver),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = vm.onPhotoSaved(output.savedUri)
                override fun onError(exc: ImageCaptureException) =
                    vm.onError("No se pudo tomar la foto: ${exc.message ?: "error desconocido"}")
            }
        )
    }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding().navigationBarsPadding()) {

            // Barra superior
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.hasFlash) {
                    IconButton(onClick = vm::cycleFlash) {
                        val icon = when (state.flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            else -> Icons.Filled.FlashOff
                        }
                        Icon(icon, "Flash", tint = Color.White)
                    }
                }
                TextButton(onClick = vm::cycleTimer) {
                    Icon(Icons.Filled.Timer, "Temporizador", tint = Color.White)
                    Text(if (state.timerSeconds == 0) " Off" else " ${state.timerSeconds}s", color = Color.White)
                }
                Box(Modifier.weight(1f))
                ResolutionMenu(state.sizes, state.selectedSize) { vm.selectSize(it) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Ajustes", tint = Color.White) }
            }

            // Vista previa
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize().pointerInput(camera) {
                        detectTapGestures { offset ->
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    }
                )
                state.lastInfo?.let {
                    Text(
                        "${it.width}×${it.height} · ${String.format(Locale.US, "%.1f", it.sizeBytes / 1_048_576.0)} MB",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                            .background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (state.countdown > 0) {
                    Text("${state.countdown}", color = Color.White, fontSize = 96.sp, modifier = Modifier.align(Alignment.Center))
                }
            }

            // Zoom y exposición
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (state.zoomSupported) {
                    Text("Zoom ${String.format(Locale.US, "%.1f", state.zoomRatio)}×", color = Color.White, fontSize = 12.sp)
                    Slider(value = state.zoomRatio, onValueChange = vm::setZoom, valueRange = state.minZoom..state.maxZoom)
                }
                if (state.exposureSupported) {
                    Text("Exposición ${state.exposureIndex}", color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = state.exposureIndex.toFloat(),
                        onValueChange = { vm.setExposure(it.roundToInt()) },
                        valueRange = state.exposureMin.toFloat()..state.exposureMax.toFloat()
                    )
                }
            }

            // Barra inferior
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A))
                        .semantics { contentDescription = "Abrir última foto" }
                        .clickable(enabled = state.lastPhotoUri != null) {
                            state.lastPhotoUri?.let(onOpenPhoto)
                        }
                ) {
                    state.lastThumb?.let {
                        Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }

                // Botón de captura
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(if (state.isCapturing) Color.Gray else Color.White)
                        .border(4.dp, Color(0xFF2A2A2A), CircleShape)
                        .semantics { contentDescription = "Tomar foto" }
                        .clickable { vm.shutter { takePhoto() } }
                )

                IconButton(onClick = vm::toggleCamera, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.FlipCameraAndroid, "Cambiar de cámara", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ResolutionMenu(sizes: List<PhotoSize>, selected: PhotoSize?, onSelect: (PhotoSize?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected?.shortLabel ?: "Máx" + (sizes.firstOrNull()?.let { " · ${it.shortLabel}" } ?: ""), color = Color.White)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Máxima disponible (auto)") }, onClick = { onSelect(null); expanded = false })
            sizes.forEach { size ->
                DropdownMenuItem(text = { Text(size.label) }, onClick = { onSelect(size); expanded = false })
            }
        }
    }
}
