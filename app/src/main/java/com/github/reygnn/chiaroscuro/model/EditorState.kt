package com.github.reygnn.chiaroscuro.model

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class EditorState(
    val sourceBitmap: Bitmap? = null,
    // Black rectangle – size only, position = canvas center
    val rectWidth: Int = 100,
    val rectHeight: Int = 100,
    val rectVisible: Boolean = false,
    // Zoom & Pan (preview only)
    val zoomScale: Float = 1f,
    val zoomOffset: Offset = Offset.Zero,
    // Canvas size – needed to back-calculate image coords on export
    val canvasSize: Size = Size.Zero,
    // AMOLED
    val amoledThreshold: Int = 10,
    val amoledWarmMode: Boolean = false,
    val amoledAnalysisBitmap: Bitmap? = null,
    val amoledPixelCount: Int = 0,
    val amoledPercent: Float = 0f,
    val isAnalyzing: Boolean = false,
    val showAmoledOverlay: Boolean = false,
    // UI
    val isLoading: Boolean = false,
    val exportMessage: String? = null,
    val proposedFilename: String? = null
)