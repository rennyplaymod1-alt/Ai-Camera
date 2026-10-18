package com.aicameraultra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicameraultra.imageprocessing.ExportSize
import com.aicameraultra.network.BackendUrl
import com.aicameraultra.performance.PerformanceMode
import com.aicameraultra.settings.AppSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: AppSettings, onChange: (AppSettings) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text("Ajustes", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }

        Section("Captura")
        Text("Calidad JPEG: ${settings.jpegQuality}", color = Color.White)
        Slider(
            value = settings.jpegQuality.toFloat(),
            onValueChange = { onChange(settings.copy(jpegQuality = it.roundToInt())) },
            valueRange = 80f..100f
        )
        Text("Más alta = archivos más grandes. 95 es un buen equilibrio.", color = Color.Gray, fontSize = 12.sp)

        Section("Smart Performance Mode")
        PerformanceMode.entries.forEach { mode ->
            OptionRow(mode.label, mode.description, settings.performanceMode == mode) {
                onChange(settings.copy(performanceMode = mode))
            }
        }

        Section("Exportación por defecto")
        ExportSize.entries.forEach { size ->
            OptionRow(size.label, "", settings.exportSize == size) {
                onChange(settings.copy(exportSize = size))
            }
        }

        Section("Servidor de IA (opcional)")
        var url by remember(settings.backendUrl) { mutableStateOf(settings.backendUrl) }
        val valid = url.isBlank() || BackendUrl.normalize(url) != null
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                onChange(settings.copy(backendUrl = it.trim()))
            },
            label = { Text("Dirección del backend (https://…)") },
            singleLine = true,
            isError = !valid,
            supportingText = {
                Text(
                    when {
                        url.isBlank() -> "Sin servidor, la IA está desactivada. La cámara funciona igual."
                        !valid -> "Debe empezar por https://"
                        else -> "Dirección válida"
                    }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun OptionRow(label: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(label, color = Color.White)
            if (description.isNotEmpty()) Text(description, color = Color.Gray, fontSize = 12.sp)
        }
    }
}
