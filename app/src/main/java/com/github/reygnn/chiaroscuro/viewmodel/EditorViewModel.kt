package com.github.reygnn.chiaroscuro.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.reygnn.chiaroscuro.model.EditorState
import com.github.reygnn.chiaroscuro.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // ── Bild laden ──────────────────────────────────────────────
    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _state.update { _ -> EditorState(isLoading = true) }
            val bitmap = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }
            _state.update { _ -> EditorState(sourceBitmap = bitmap) }
        }
    }

    // ── Schwarzes Rechteck ───────────────────────────────────────
    fun setRectWidth(value: Int) {
        _state.update { current ->
            val maxX = ((current.sourceBitmap?.width ?: 200) - value).coerceAtLeast(0).toFloat()
            current.copy(rectWidth = value, rectOffset = current.rectOffset.copy(x = current.rectOffset.x.coerceAtMost(maxX)))
        }
    }

    fun setRectHeight(value: Int) {
        _state.update { current ->
            val maxY = ((current.sourceBitmap?.height ?: 200) - value).coerceAtLeast(0).toFloat()
            current.copy(rectHeight = value, rectOffset = current.rectOffset.copy(y = current.rectOffset.y.coerceAtMost(maxY)))
        }
    }

    fun moveRect(delta: Offset) {
        _state.update { current ->
            val maxX = ((current.sourceBitmap?.width ?: 200) - current.rectWidth).coerceAtLeast(0).toFloat()
            val maxY = ((current.sourceBitmap?.height ?: 200) - current.rectHeight).coerceAtLeast(0).toFloat()
            current.copy(rectOffset = Offset(
                (current.rectOffset.x + delta.x).coerceIn(0f, maxX),
                (current.rectOffset.y + delta.y).coerceIn(0f, maxY)
            ))
        }
    }

    fun toggleRect() {
        _state.update { current ->
            if (!current.rectVisible) {
                val bitmap = current.sourceBitmap
                val centerX = ((bitmap?.width ?: 200) - current.rectWidth) / 2f
                val centerY = ((bitmap?.height ?: 200) - current.rectHeight) / 2f
                current.copy(rectVisible = true, rectOffset = Offset(centerX.coerceAtLeast(0f), centerY.coerceAtLeast(0f)))
            } else {
                current.copy(rectVisible = false)
            }
        }
    }

    // ── FAB – runs all enabled steps from Preferences ────────────
    fun applyQuickAction() {
        viewModelScope.launch {
            val p = prefs.prefs.first()
            val src = _state.value.sourceBitmap ?: return@launch

            _state.update { it.copy(isAnalyzing = true) }

            var current = src

            // Step 1: AMOLED correction
            if (p.fabApplyAmoled) {
                current = withContext(Dispatchers.IO) {
                    applyAmoled(current, p.amoledThreshold, p.amoledWarmMode)
                }
            }

            // Step 2: Place rectangle
            val rectVisible = p.fabPlaceRect
            val rectOffset  = Offset(p.rectX, p.rectY)

            _state.update { it.copy(
                sourceBitmap = current,
                isAnalyzing  = false,
                rectVisible  = rectVisible,
                rectWidth    = p.rectWidth,
                rectHeight   = p.rectHeight,
                rectOffset   = rectOffset,
                proposedFilename = nextSleeveName(p.sleeveCounter)
            )}
        }
    }

    private fun applyAmoled(src: Bitmap, threshold: Int, warmMode: Boolean): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
            val isNearBlack = r <= threshold && g <= threshold && b <= threshold && (r > 0 || g > 0 || b > 0)
            val hit = if (warmMode) isNearBlack && (r - b) > 3 else isNearBlack
            if (hit) pixels[i] = Color.BLACK
        }
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    // ── AMOLED manual ────────────────────────────────────────────
    fun setAmoledThreshold(value: Int) {
        _state.update { it.copy(amoledThreshold = value, amoledAnalysisBitmap = null, showAmoledOverlay = false) }
    }

    fun toggleAmoledWarmMode() {
        _state.update { it.copy(amoledWarmMode = !it.amoledWarmMode, amoledAnalysisBitmap = null, showAmoledOverlay = false) }
    }

    fun analyzeAmoled() {
        val src = _state.value.sourceBitmap ?: return
        val threshold = _state.value.amoledThreshold
        val warmMode  = _state.value.amoledWarmMode
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val (analysisBitmap, count) = withContext(Dispatchers.IO) {
                val w = src.width; val h = src.height
                val pixels = IntArray(w * h)
                src.getPixels(pixels, 0, w, 0, 0, w, h)
                var nearBlack = 0
                for (i in pixels.indices) {
                    val r = Color.red(pixels[i]); val g = Color.green(pixels[i]); val b = Color.blue(pixels[i])
                    val isNearBlack = r <= threshold && g <= threshold && b <= threshold && (r > 0 || g > 0 || b > 0)
                    val hit = if (warmMode) isNearBlack && (r - b) > 3 else isNearBlack
                    if (hit) { pixels[i] = Color.RED; nearBlack++ }
                }
                val result = src.copy(Bitmap.Config.ARGB_8888, true)
                result.setPixels(pixels, 0, w, 0, 0, w, h)
                Pair(result, nearBlack)
            }
            _state.update { it.copy(
                isAnalyzing = false,
                amoledAnalysisBitmap = analysisBitmap,
                amoledPixelCount = count,
                amoledPercent = count.toFloat() / (src.width * src.height) * 100f,
                showAmoledOverlay = true
            )}
        }
    }

    fun applyAmoledCorrection() {
        val src = _state.value.sourceBitmap ?: return
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val corrected = withContext(Dispatchers.IO) {
                applyAmoled(src, _state.value.amoledThreshold, _state.value.amoledWarmMode)
            }
            _state.update { it.copy(
                sourceBitmap = corrected, isAnalyzing = false,
                amoledAnalysisBitmap = null, showAmoledOverlay = false,
                amoledPixelCount = 0, amoledPercent = 0f,
                exportMessage = "✅ AMOLED correction applied"
            )}
        }
    }

    fun clearAmoledAnalysis() {
        _state.update { it.copy(amoledAnalysisBitmap = null, showAmoledOverlay = false, amoledPixelCount = 0, amoledPercent = 0f) }
    }

    // ── Export ───────────────────────────────────────────────────
    fun saveTransparent(context: Context, uri: Uri) {
        viewModelScope.launch {
            val src = _state.value.sourceBitmap ?: return@launch
            withContext(Dispatchers.IO) {
                try {
                    val base = src.copy(Bitmap.Config.ARGB_8888, true)
                    if (_state.value.rectVisible) {
                        val canvas = android.graphics.Canvas(base)
                        val paint = android.graphics.Paint().apply { color = Color.BLACK }
                        canvas.drawRect(
                            _state.value.rectOffset.x, _state.value.rectOffset.y,
                            _state.value.rectOffset.x + _state.value.rectWidth,
                            _state.value.rectOffset.y + _state.value.rectHeight,
                            paint
                        )
                    }
                    val w = base.width; val h = base.height
                    val pixels = IntArray(w * h)
                    base.getPixels(pixels, 0, w, 0, 0, w, h)
                    for (i in pixels.indices) {
                        if (pixels[i] == Color.BLACK) pixels[i] = Color.TRANSPARENT
                    }
                    val result = createBitmap(w, h)
                    result.setPixels(pixels, 0, w, 0, 0, w, h)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        result.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    // Increment counter after successful save
                    prefs.incrementCounter()
                    _state.update { it.copy(exportMessage = "✅ Saved!", proposedFilename = null) }
                } catch (e: Exception) {
                    _state.update { it.copy(exportMessage = "❌ Error: ${e.message}") }
                }
            }
        }
    }

    fun clearExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    private fun nextSleeveName(counter: Int) = "sleeve_${counter.toString().padStart(3, '0')}.png"
}