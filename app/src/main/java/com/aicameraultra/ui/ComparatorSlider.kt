
package com.aicameraultra.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale

/** Comparador antes/después: el lado izquierdo muestra el original y el derecho el resultado. */
@Composable
fun ComparatorSlider(
    before: ImageBitmap,
    after: ImageBitmap,
    position: Float,
    onPosition: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (size.width > 0) {
                        onPosition(
                            (change.position.x / size.width).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onPosition(
                            (offset.x / size.width).coerceIn(0f, 1f)
                        )
                    }
                }
            }
    ) {
        Image(
            after,
            "Resultado con IA",
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Image(
            before,
            "Foto original",
            Modifier.fillMaxSize().drawWithContent {
                clipRect(0f, 0f, size.width * position, size.height) {
                    this@drawWithContent.drawContent()
                }

                val x = size.width * position
                drawLine(
                    Color.White,
                    Offset(x, 0f),
                    Offset(x, size.height),
                    strokeWidth = 4f
                )
            },
            contentScale = ContentScale.Fit
        )
    }
}
