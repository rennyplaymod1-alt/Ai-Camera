package com.aicameraultra.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicameraultra.enhance.EnhancePreset
import com.aicameraultra.enhance.EnhanceUiState
import com.aicameraultra.enhance.EnhanceViewModel
import com.aicameraultra.imageprocessing.ExportSize

@Composable
fun EnhanceScreen(vm: EnhanceViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(containerColor = Color.Black, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
                }
                Text("Mejorar con IA", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }

            val before = state.sourceBitmap
            val after = state.resultBitmap
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color(0xFF111111))) {
                when {
                    before != null && after != null -> ComparatorSlider(
                        before.asImageBitmap(), after.asImageBitmap(),
                        state.sliderPos, vm::setSlider, Modifier.fillMaxSize()
                    )
                    before != null -> Image(
                        before.asImageBitmap(), "Foto original",
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                }
            }

            Text(infoText(state), color = Color.Gray, fontSize = 12.sp)

            Text("Tipo de mejora", color = Color.White)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancePreset.entries.forEach { preset ->
                    FilterChip(
                        selected = state.preset == preset,
                        onClick = { vm.setPreset(preset) },
                        label = { Text(preset.label) },
                        enabled = !state.busy
                    )
                }
            }

            OutlinedTextField(
                value = state.instructions,
                onValueChange = vm::setInstructions,
                label = { Text("Instrucciones adicionales (opcional)") },
                supportingText = { Text("${state.instructions.length}/300") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                enabled = !state.busy
            )

            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.progress ?: "Procesando…", color = Color.White, fontSize = 13.sp)
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            } else {
                Button(
                    onClick = vm::enhance,
                    enabled = state.source != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (state.result == null) "Mejorar con IA" else "Volver a mejorar")
                }
            }

            Text("Exportar copia (la original no se modifica)", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportSize.entries.forEach { size ->
                    OutlinedButton(
                        onClick = { vm.exportCopy(size) },
                        enabled = !state.busy && state.source != null
                    ) { Text(size.shortLabel) }
                }
            }

            Text(
                "La IA es opcional: la cámara y la galería funcionan sin conexión. " +
                    "Las copias 4K se marcan como escaladas si la captura original no es 4K nativa.",
                color = Color.Gray, fontSize = 12.sp
            )
            Text("", modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

private fun infoText(s: EnhanceUiState): String = buildString {
    append(s.sourceInfo?.let { "Original: ${it.width}×${it.height}" } ?: "Cargando original…")
    s.resultInfo?.let {
        append("  ·  Resultado IA: ${it.width}×${it.height}")
        append("\nLas dimensiones del resultado las decide el servicio de IA; no aumentan la resolución real.")
    }
}
