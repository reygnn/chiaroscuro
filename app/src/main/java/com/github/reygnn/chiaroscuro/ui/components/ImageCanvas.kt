package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.reygnn.chiaroscuro.model.EditorState

private val RECT_PREVIEW_FILL   = Color(0x55FF3333)
private val RECT_PREVIEW_BORDER = Color(0xCCFF3333)

@Composable
fun ImageCanvas(
    state: EditorState,
    onDragRect: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayBitmap = if (state.showAmoledOverlay && state.amoledAnalysisBitmap != null)
        state.amoledAnalysisBitmap else state.sourceBitmap

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1C))
            .pointerInput(state.rectVisible) {
                if (state.rectVisible) {
                    detectDragGestures { _, dragAmount -> onDragRect(dragAmount) }
                }
            }
    ) {
        if (displayBitmap == null) { drawNoImagePlaceholder(this); return@Canvas }

        val scale   = minOf(size.width / displayBitmap.width, size.height / displayBitmap.height)
        val drawW   = displayBitmap.width  * scale
        val drawH   = displayBitmap.height * scale
        val offsetX = (size.width  - drawW) / 2f
        val offsetY = (size.height - drawH) / 2f

        drawImage(
            image     = displayBitmap.asImageBitmap(),
            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
            dstSize   = IntSize(drawW.toInt(), drawH.toInt())
        )

        // Schwarzes Rechteck – rot im Preview
        if (state.rectVisible && !state.showAmoledOverlay) {
            val rx = offsetX + state.rectOffset.x * scale
            val ry = offsetY + state.rectOffset.y * scale
            val rw = state.rectWidth  * scale
            val rh = state.rectHeight * scale
            drawRect(color = RECT_PREVIEW_FILL,   topLeft = Offset(rx, ry), size = Size(rw, rh))
            drawRect(color = RECT_PREVIEW_BORDER, topLeft = Offset(rx, ry), size = Size(rw, rh), style = Stroke(width = 2f))
        }
    }
}

private fun drawNoImagePlaceholder(scope: DrawScope) {
    scope.drawLine(color = Color(0xFF555555),
        start = Offset(scope.size.width * 0.3f, scope.size.height * 0.5f),
        end   = Offset(scope.size.width * 0.7f, scope.size.height * 0.5f), strokeWidth = 4f)
    scope.drawLine(color = Color(0xFF555555),
        start = Offset(scope.size.width * 0.5f, scope.size.height * 0.3f),
        end   = Offset(scope.size.width * 0.5f, scope.size.height * 0.7f), strokeWidth = 4f)
}