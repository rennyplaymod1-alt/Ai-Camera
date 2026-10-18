package com.aicameraultra

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aicameraultra.camera.CameraViewModel
import com.aicameraultra.enhance.EnhanceViewModel
import com.aicameraultra.ui.CameraScreen
import com.aicameraultra.ui.EnhanceScreen
import com.aicameraultra.ui.PermissionGate
import com.aicameraultra.ui.SettingsScreen
import com.aicameraultra.ui.theme.AICameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AICameraTheme {
                PermissionGate { AppRoot() }
            }
        }
    }
}

private sealed interface Screen {
    data object Camera : Screen
    data object Settings : Screen
    data class Enhance(val uri: Uri) : Screen
}

@Composable
private fun AppRoot() {
    val cameraVm: CameraViewModel = viewModel()
    val enhanceVm: EnhanceViewModel = viewModel()
    var screen by remember { mutableStateOf<Screen>(Screen.Camera) }

    BackHandler(enabled = screen !is Screen.Camera) { screen = Screen.Camera }

    when (val current = screen) {
        Screen.Camera -> CameraScreen(
            vm = cameraVm,
            onOpenSettings = { screen = Screen.Settings },
            onOpenPhoto = { screen = Screen.Enhance(it) }
        )
        Screen.Settings -> {
            val state by cameraVm.state.collectAsState()
            SettingsScreen(state.settings, cameraVm::updateSettings) { screen = Screen.Camera }
        }
        is Screen.Enhance -> {
            LaunchedEffect(current.uri) { enhanceVm.setSource(current.uri) }
            EnhanceScreen(enhanceVm) { screen = Screen.Camera }
        }
    }
}
