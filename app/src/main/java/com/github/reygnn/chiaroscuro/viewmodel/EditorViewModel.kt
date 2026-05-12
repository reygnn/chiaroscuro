package com.github.reygnn.chiaroscuro.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.chiaroscuro.ChiaroscuroApplication
import com.github.reygnn.chiaroscuro.imaging.BitmapLoader
import com.github.reygnn.chiaroscuro.imaging.ContentResolverBitmapLoader
import com.github.reygnn.chiaroscuro.imaging.ImageGeometry
import com.github.reygnn.chiaroscuro.imaging.ImageProcessing
import com.github.reygnn.chiaroscuro.model.EditorState
import com.github.reygnn.chiaroscuro.model.ExportMessage
import com.github.reygnn.chiaroscuro.preferences.ExportBackground
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
    private val bitmapLoader: BitmapLoader = ContentResolverBitmapLoader,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _analysisBitmap = MutableStateFlow<Bitmap?>(null)
    val analysisBitmap: StateFlow<Bitmap?> = _analysisBitmap.asStateFlow()

    init {
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

    /**
     * Resets the editor for a fresh image and triggers an async decode.
     *
     * **Implementation note — reset list, not allowlist.**
     *
     * Every transient field is written explicitly in the `copy(...)`
     * call. `canvasSize` is omitted, which `copy` preserves: it is
     * reported from Compose layout via `Modifier.onSizeChanged` in
     * [com.github.reygnn.chiaroscuro.ui.components.ImageCanvas], and
     * that callback fires only when the layout size actually changes —
     * so field-level zeroing here would never be re-populated. The
     * export pipeline's `canvasSize != Size.Zero` guard (see
     * [writeExport]) would then silently drop the watermark-cover
     * rectangle from the saved PNG: the preview shows the rectangle in
     * place, the export omits it.
     *
     * The previous wholesale-replace shape (`_state.value = EditorState(...)`)
     * made the common transient case automatic and the rare
     * layout-sourced case silently wrong — i.e. exactly the bug this
     * method is fixing. The reset-list shape forces every future
     * `EditorState` field to take an explicit position: name it here
     * to reset, omit it to inherit "preserved".
     */
    fun loadImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val p = repository.settings.first()
            _sourceBitmap.value = null
            _analysisBitmap.value = null
            _state.update {
                it.copy(
                    rectWidth         = p.rectWidth,
                    rectHeight        = p.rectHeight,
                    rectVisible       = false,
                    zoomScale         = 1f,
                    zoomOffset        = Offset.Zero,
                    // canvasSize: by omission, preserved (layout-sourced).
                    amoledThreshold   = p.amoledThreshold,
                    amoledWarmMode    = p.amoledWarmMode,
                    amoledPixelCount  = 0,
                    amoledPercent     = 0f,
                    isAnalyzing       = false,
                    showAmoledOverlay = false,
                    isLoading         = true,
                    exportMessage     = null,
                    proposedFilename  = null,
                )
            }
            val bitmap = bitmapLoader.load(context, uri)
            _sourceBitmap.value = bitmap
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun updateCanvasSize(size: Size) {
        _state.update { it.copy(canvasSize = size) }
    }

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

    fun applyQuickAction() {
        val src = _sourceBitmap.value ?: return
        viewModelScope.launch {
            val p = repository.settings.first()
            _state.update { it.copy(isAnalyzing = true) }
            var current = src
            if (p.fabApplyAmoled) {
                current = withContext(Dispatchers.IO) {
                    ImageProcessing.applyAmoledCorrection(current, p.amoledThreshold, p.amoledWarmMode)
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
            val analysis = withContext(Dispatchers.IO) {
                ImageProcessing.analyzeAmoled(src, threshold, warmMode)
            }
            _analysisBitmap.value = analysis.bitmap
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    amoledPixelCount  = analysis.nearBlackCount,
                    amoledPercent     = analysis.nearBlackCount.toFloat() / (src.width * src.height) * 100f,
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
                ImageProcessing.applyAmoledCorrection(
                    src,
                    _state.value.amoledThreshold,
                    _state.value.amoledWarmMode,
                )
            }
            _sourceBitmap.value = corrected
            _analysisBitmap.value = null
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    showAmoledOverlay = false,
                    amoledPixelCount  = 0,
                    amoledPercent     = 0f,
                    exportMessage     = ExportMessage.AmoledApplied,
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

    fun saveTransparent(context: Context, uri: Uri) {
        val src = _sourceBitmap.value ?: return
        val s = _state.value
        viewModelScope.launch {
            val background = repository.settings.first().exportBackground
            val message: ExportMessage = withContext(Dispatchers.IO) {
                runCatching {
                    writeExport(context, uri, src, s, background)
                    repository.incrementCounter()
                    ExportMessage.Saved as ExportMessage
                }.getOrElse { e ->
                    ExportMessage.Error(e.message)
                }
            }
            _state.update {
                it.copy(
                    exportMessage    = message,
                    proposedFilename = if (message is ExportMessage.Saved) null else it.proposedFilename,
                )
            }
        }
    }

    fun clearExportMessage() {
        _state.update { it.copy(exportMessage = null) }
    }

    /**
     * Renders the final PNG.
     *
     * Depending on [background]:
     *   - [ExportBackground.AMOLED] leaves pure-black pixels black (the
     *     AMOLED display will drive those pixels fully off at render
     *     time). Output is fully opaque.
     *   - [ExportBackground.TRANSPARENT] replaces pure-black pixels
     *     with alpha=0. Output uses the PNG alpha channel.
     *
     * In both modes, any black rectangle drawn by [ImageProcessing.drawBlackRect]
     * is applied first, so the mode switch decides how that rectangle's
     * pixels are treated in the final image.
     */
    private fun writeExport(
        context: Context,
        uri: Uri,
        src: Bitmap,
        s: EditorState,
        background: ExportBackground,
    ) {
        val working = src.copy(Bitmap.Config.ARGB_8888, true)
        if (s.rectVisible && s.canvasSize != Size.Zero) {
            val origin = ImageGeometry.computeRectOriginInImage(
                imageWidth = working.width,
                imageHeight = working.height,
                canvasWidth = s.canvasSize.width,
                canvasHeight = s.canvasSize.height,
                rectWidth = s.rectWidth,
                rectHeight = s.rectHeight,
                zoomScale = s.zoomScale,
                zoomOffsetX = s.zoomOffset.x,
                zoomOffsetY = s.zoomOffset.y,
            )
            ImageProcessing.drawBlackRect(working, origin.x, origin.y, s.rectWidth, s.rectHeight)
        }
        val result = when (background) {
            ExportBackground.AMOLED -> working
            ExportBackground.TRANSPARENT -> ImageProcessing.blackToTransparent(working)
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            result.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: error("Cannot open output stream for $uri")
    }

    private fun nextFilename(counter: Int, prefix: String): String =
        "${prefix}_${counter.toString().padStart(FILENAME_COUNTER_PAD, '0')}.png"

    companion object {
        private const val ZOOM_MIN = 0.5f
        private const val ZOOM_MAX = 8f

        private const val FILENAME_COUNTER_PAD = 3

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ChiaroscuroApplication
                EditorViewModel(app.preferencesRepository)
            }
        }
    }
}
