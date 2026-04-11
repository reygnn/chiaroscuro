package com.github.reygnn.chiaroscuro.model

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset

data class EditorState(
    val sourceBitmap: Bitmap? = null,
    // Schwarzes Rechteck
    val rectWidth: Int = 100,
    val rectHeight: Int = 100,
    val rectOffset: Offset = Offset.Zero,
    val rectVisible: Boolean = false,
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