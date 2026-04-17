package com.github.reygnn.chiaroscuro.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

data class EditorState(
    val rectWidth: Int = 100,
    val rectHeight: Int = 100,
    val rectVisible: Boolean = false,
    val zoomScale: Float = 1f,
    val zoomOffset: Offset = Offset.Zero,
    val canvasSize: Size = Size.Zero,
    val amoledThreshold: Int = 10,
    val amoledWarmMode: Boolean = false,
    val amoledPixelCount: Int = 0,
    val amoledPercent: Float = 0f,
    val isAnalyzing: Boolean = false,
    val showAmoledOverlay: Boolean = false,
    val isLoading: Boolean = false,
    val exportMessage: ExportMessage? = null,
    val proposedFilename: String? = null,
)