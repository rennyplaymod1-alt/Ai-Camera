package com.aicameraultra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFFFC857),
    onPrimary = Color(0xFF1A1A1A),
    background = Color.Black,
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEDED)
)

@Composable
fun AICameraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
