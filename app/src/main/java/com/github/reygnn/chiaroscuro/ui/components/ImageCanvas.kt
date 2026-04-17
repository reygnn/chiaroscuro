package com.github.reygnn.chiaroscuro.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.github.reygnn.chiaroscuro.model.EditorState

private val RECT_PREVIEW_FILL   = Color(0x55FF3333)
private val RECT_PREVIEW_BORDER = Color(0xCCFF3333)
private val CANVAS_BG           = Color(0xFF1C1C1C)
private val PLACEHOLDER_COLOR   = Color(0xFF555555)

@Composable
fun ImageCanvas(
    state: EditorState,
    sourceBitmap: Bitmap?,
    analysisBitmap: Bitmap?,
    onZoomChange: (scaleChange: Float, offsetChange: Offset) -> Unit,
    onDoubleTap: () -> Unit,
    onCanvasSize: (Size) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayBitmap = if (state.showAmoledOverlay && analysisBitmap != null) {
        analysisBitmap
    } else {
        sourceBitmap
    }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        onZoomChange(zoomChange, offsetChange)
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(CANVAS_BG)
            .onSizeChanged { onCanvasSize(it.toSize()) }
            .transformable(state = transformableState, enabled = sourceBitmap != null)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
    ) {
        if (displayBitmap == null) {
            drawNoImagePlaceholder(this)
            return@Canvas
        }

        val baseScale = minOf(
            size.width / displayBitmap.width,
            size.height / displayBitmap.height,
        )
        val drawW = displayBitmap.width * baseScale
        val drawH = displayBitmap.height * baseScale
        val baseOffX = (size.width - drawW) / 2f
        val baseOffY = (size.height - drawH) / 2f

        withTransform({
            translate(state.zoomOffset.x, state.zoomOffset.y)
            scale(state.zoomScale, state.zoomScale, Offset(size.width / 2f, size.height / 2f))
        }) {
            drawImage(
                image = displayBitmap.asImageBitmap(),
                dstOffset = IntOffset(baseOffX.toInt(), baseOffY.toInt()),
                dstSize = IntSize(drawW.toInt(), drawH.toInt()),
            )
        }

        // Rectangle stays at canvas center, outside the zoom transform,
        // so the user pans the image *under* the fixed rect.
        if (state.rectVisible && !state.showAmoledOverlay) {
            val rectScreenW = state.rectWidth * baseScale * state.zoomScale
            val rectScreenH = state.rectHeight * baseScale * state.zoomScale
            val rx = size.width / 2f - rectScreenW / 2f
            val ry = size.height / 2f - rectScreenH / 2f
            drawRect(
                color = RECT_PREVIEW_FILL,
                topLeft = Offset(rx, ry),
                size = Size(rectScreenW, rectScreenH),
            )
            drawRect(
                color = RECT_PREVIEW_BORDER,
                topLeft = Offset(rx, ry),
                size = Size(rectScreenW, rectScreenH),
                style = Stroke(width = 2f),
            )
        }
    }
}

private fun drawNoImagePlaceholder(scope: DrawScope) {
    with(scope) {
        drawLine(
            color = PLACEHOLDER_COLOR,
            start = Offset(size.width * 0.3f, size.height * 0.5f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = 4f,
        )
        drawLine(
            color = PLACEHOLDER_COLOR,
            start = Offset(size.width * 0.5f, size.height * 0.3f),
            end = Offset(size.width * 0.5f, size.height * 0.7f),
            strokeWidth = 4f,
        )
    }
}