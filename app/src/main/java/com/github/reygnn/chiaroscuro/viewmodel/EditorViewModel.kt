package com.github.reygnn.chiaroscuro.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.chiaroscuro.ChiaroscuroApplication
import com.github.reygnn.chiaroscuro.model.EditorState
import com.github.reygnn.chiaroscuro.preferences.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(
    private val repository: PreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Bitmaps live outside EditorState so that data class semantics
    // (equals/copy/toString) stay value-typed and Compose recomposition
    // scope is sharper — numeric sliders don't bust bitmap-bound composables.
    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _analysisBitmap = MutableStateFlow<Bitmap?>(null)
    val analysisBitmap: StateFlow<Bitmap?> = _analysisBitmap.asStateFlow()

    init {
        // Keep editor state synced with persisted preference changes made
        // elsewhere (e.g. PreferencesScreen). Local setters still update
        // _state eagerly for responsiveness; the collect is idempotent
        // when the change originated here and authoritative when it didn't.
        viewModelScope.launch {
            repository.settings.collect { p ->
                _state.update {
                    it.copy(
                        amoledThreshold = p.amoledThreshold,
                        amoledWarmMode  = p.amoledWarmMode,
                        rectWidth       = p.rectWidth,
                        rectHeight      = p.rectHeight,
                    )
                }
            }
        }
    }

    // ── Image load ───────────────────────────────────────────────
    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val p = repository.settings.first()
            _sourceBitmap.value = null
            _analysisBitmap.value = null
            _state.value = EditorState(
                amoledThreshold = p.amoledThreshold,
                amoledWarmMode  = p.amoledWarmMode,
                rectWidth       = p.rectWidth,
                rectHeight      = p.rectHeight,
                isLoading       = true,
            )
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
            _sourceBitmap.value = bitmap
            _state.update { it.copy(isLoading = false) }
        }
    }

    // ── Canvas size ──────────────────────────────────────────────
    fun updateCanvasSize(size: Size) {
        _state.update { it.copy(canvasSize = size) }
    }

    // ── Rectangle ────────────────────────────────────────────────
    fun setRectWidth(value: Int) {
        _state.update { it.copy(rectWidth = value) }
        viewModelScope.launch { repository.setRectWidth(value) }
    }

    fun setRectHeight(value: Int) {
        _state.update { it.copy(rectHeight = value) }
        viewModelScope.launch { repository.setRectHeight(value) }
    }

    fun toggleRect() {
        _state.update { it.copy(rectVisible = !it.rectVisible) }
    }

    // ── Zoom & Pan ───────────────────────────────────────────────
    fun updateZoom(scaleChange: Float, offsetChange: Offset) {
        _state.update { current ->
            current.copy(
                zoomScale  = (current.zoomScale * scaleChange).coerceIn(ZOOM_MIN, ZOOM_MAX),
                zoomOffset = current.zoomOffset + offsetChange,
            )
        }
    }

    fun resetZoom() {
        _state.update { it.copy(zoomScale = 1f, zoomOffset = Offset.Zero) }
    }

    // ── FAB Quick Action ─────────────────────────────────────────
    fun applyQuickAction() {
        val src = _sourceBitmap.value ?: return
        viewModelScope.launch {
            val p = repository.settings.first()
            _state.update { it.copy(isAnalyzing = true) }
            var current = src
            if (p.fabApplyAmoled) {
                current = withContext(Dispatchers.IO) {
                    applyAmoled(current, p.amoledThreshold, p.amoledWarmMode)
                }
            }
            _sourceBitmap.value = current
            _state.update {
                it.copy(
                    isAnalyzing      = false,
                    rectVisible      = p.fabPlaceRect,
                    rectWidth        = p.rectWidth,
                    rectHeight       = p.rectHeight,
                    proposedFilename = nextFilename(p.sleeveCounter, p.filenamePrefix),
                )
            }
        }
    }

    // ── AMOLED ───────────────────────────────────────────────────
    fun setAmoledThreshold(value: Int) {
        _analysisBitmap.value = null
        _state.update { it.copy(amoledThreshold = value, showAmoledOverlay = false) }
        viewModelScope.launch { repository.setAmoledThreshold(value) }
    }

    fun toggleAmoledWarmMode() {
        val newValue = !_state.value.amoledWarmMode
        _analysisBitmap.value = null
        _state.update { it.copy(amoledWarmMode = newValue, showAmoledOverlay = false) }
        viewModelScope.launch { repository.setAmoledWarmMode(newValue) }
    }

    fun analyzeAmoled() {
        val src = _sourceBitmap.value ?: return
        val threshold = _state.value.amoledThreshold
        val warmMode = _state.value.amoledWarmMode
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val (result, count) = withContext(Dispatchers.IO) {
                analyzeBitmap(src, threshold, warmMode)
            }
            _analysisBitmap.value = result
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    amoledPixelCount  = count,
                    amoledPercent     = count.toFloat() / (src.width * src.height) * 100f,
                    showAmoledOverlay = true,
                )
            }
        }
    }

    fun applyAmoledCorrection() {
        val src = _sourceBitmap.value ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val corrected = withContext(Dispatchers.IO) {
                applyAmoled(src, _state.value.amoledThreshold, _state.value.amoledWarmMode)
            }
            _sourceBitmap.value = corrected
            _analysisBitmap.value = null
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    showAmoledOverlay = false,
                    amoledPixelCount  = 0,
                    amoledPercent     = 0f,
                    exportMessage     = MESSAGE_AMOLED_APPLIED,
                )
            }
        }
    }

    fun clearAmoledAnalysis() {
        _analysisBitmap.value = null
        _state.update {
            it.copy(
                showAmoledOverlay = false,
                amoledPixelCount  = 0,
                amoledPercent     = 0f,
            )
        }
    }

    // ── Export ───────────────────────────────────────────────────
    fun saveTransparent(context: Context, uri: Uri) {
        val src = _sourceBitmap.value ?: return
        val s = _state.value
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    writeTransparentPng(context, uri, src, s)
                    repository.incrementCounter()
                    MESSAGE_SAVED
                }.getOrElse { e ->
                    "$MESSAGE_ERROR_PREFIX${e.message}"
                }
            }
            _state.update {
                it.copy(
                    exportMessage    = message,
                    proposedFilename = if (message == MESSAGE_SAVED) null else it.proposedFilename,
                )
            }
        }
    }

    fun clearExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    private fun writeTransparentPng(
        context: Context,
        uri: Uri,
        src: Bitmap,
        s: EditorState,
    ) {
        val base = src.copy(Bitmap.Config.ARGB_8888, true)
        if (s.rectVisible && s.canvasSize != Size.Zero) {
            drawRectOntoBitmap(base, s)
        }
        val w = base.width
        val h = base.height
        val pixels = IntArray(w * h)
        base.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            if (pixels[i] == Color.BLACK) pixels[i] = Color.TRANSPARENT
        }
        val result = createBitmap(w, h)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            result.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: error("Cannot open output stream for $uri")
    }

    private fun drawRectOntoBitmap(base: Bitmap, s: EditorState) {
        val canvasW = s.canvasSize.width
        val canvasH = s.canvasSize.height
        val baseScale = minOf(canvasW / base.width, canvasH / base.height)
        val baseOffX = (canvasW - base.width * baseScale) / 2f
        val baseOffY = (canvasH - base.height * baseScale) / 2f
        val rectScreenW = s.rectWidth * baseScale * s.zoomScale
        val rectScreenH = s.rectHeight * baseScale * s.zoomScale
        val screenX = canvasW / 2f - rectScreenW / 2f
        val screenY = canvasH / 2f - rectScreenH / 2f
        val canvasX = (screenX - s.zoomOffset.x - canvasW / 2f) / s.zoomScale + canvasW / 2f
        val canvasY = (screenY - s.zoomOffset.y - canvasH / 2f) / s.zoomScale + canvasH / 2f
        val imgX = ((canvasX - baseOffX) / baseScale).toInt()
        val imgY = ((canvasY - baseOffY) / baseScale).toInt()
        val canvas = Canvas(base)
        val paint = Paint().apply { color = Color.BLACK }
        canvas.drawRect(
            imgX.toFloat(),
            imgY.toFloat(),
            (imgX + s.rectWidth).toFloat(),
            (imgY + s.rectHeight).toFloat(),
            paint,
        )
    }

    private fun nextFilename(counter: Int, prefix: String): String =
        "${prefix}_${counter.toString().padStart(FILENAME_COUNTER_PAD, '0')}.png"

    companion object {
        private const val ZOOM_MIN = 0.5f
        private const val ZOOM_MAX = 8f

        private const val FILENAME_COUNTER_PAD = 3

        private const val MESSAGE_SAVED           = "✅ Saved!"
        private const val MESSAGE_AMOLED_APPLIED  = "✅ AMOLED correction applied"
        private const val MESSAGE_ERROR_PREFIX    = "❌ Error: "

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ChiaroscuroApplication
                EditorViewModel(app.preferencesRepository)
            }
        }
    }
}

// ── Pure image-processing helpers ─────────────────────────────────
// Kept as private top-level functions to make them trivially testable
// and to keep the ViewModel focused on orchestration.

private fun applyAmoled(src: Bitmap, threshold: Int, warmMode: Boolean): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val px = pixels[i]
        val r = Color.red(px)
        val g = Color.green(px)
        val b = Color.blue(px)
        if (isTargetPixel(r, g, b, threshold, warmMode)) {
            pixels[i] = Color.BLACK
        }
    }
    val result = src.copy(Bitmap.Config.ARGB_8888, true)
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

private fun analyzeBitmap(
    src: Bitmap,
    threshold: Int,
    warmMode: Boolean,
): Pair<Bitmap, Int> {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    var nearBlack = 0
    for (i in pixels.indices) {
        val px = pixels[i]
        val r = Color.red(px)
        val g = Color.green(px)
        val b = Color.blue(px)
        if (isTargetPixel(r, g, b, threshold, warmMode)) {
            pixels[i] = Color.RED
            nearBlack++
        }
    }
    val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp to nearBlack
}

private fun isTargetPixel(r: Int, g: Int, b: Int, threshold: Int, warmMode: Boolean): Boolean {
    val isNearBlack = r <= threshold && g <= threshold && b <= threshold && (r > 0 || g > 0 || b > 0)
    return if (warmMode) isNearBlack && (r - b) > WARM_TINT_MIN_DELTA else isNearBlack
}

private const val WARM_TINT_MIN_DELTA = 3