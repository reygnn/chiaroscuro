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
import com.github.reygnn.chiaroscuro.imaging.RectOrigin
import com.github.reygnn.chiaroscuro.model.EditorState
import com.github.reygnn.chiaroscuro.model.ExportMessage
import com.github.reygnn.chiaroscuro.preferences.ExportBackground
import com.github.reygnn.chiaroscuro.preferences.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class EditorViewModel(
    private val repository: PreferencesRepository,
    private val bitmapLoader: BitmapLoader = ContentResolverBitmapLoader,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Bitmap lifecycle note: bitmaps that have already been *published* to
    // these StateFlows (and are therefore being drawn by ImageCanvas) are
    // deliberately NOT recycled at the moment they are replaced. A
    // recomposition may still be mid-draw on the outgoing bitmap, and
    // recycling it under the draw would crash ("trying to use a recycled
    // bitmap"). Their native memory is reclaimed by the platform GC instead.
    // Bitmaps that were created but never published (a stale IO result
    // discarded by the loadGeneration guard) ARE recycled immediately — see
    // the guarded `recycle()` calls below — since nothing can be drawing them.
    private val _sourceBitmap = MutableStateFlow<Bitmap?>(null)
    val sourceBitmap: StateFlow<Bitmap?> = _sourceBitmap.asStateFlow()

    private val _analysisBitmap = MutableStateFlow<Bitmap?>(null)
    val analysisBitmap: StateFlow<Bitmap?> = _analysisBitmap.asStateFlow()

    /**
     * Monotonic load counter, bumped on every [loadImage] call (on Main,
     * before the decode coroutine launches).
     *
     * Guards against stale-write races: a long-running IO job
     * ([applyAmoledCorrection], [analyzeAmoled], [applyQuickAction], or a
     * slow decode) captures the generation at entry and, on resume,
     * discards its result if the user has since loaded a different image.
     * Without this, a correction of the *old* image could clobber the
     * freshly loaded new one — wrong bitmap shown/saved plus a misleading
     * "applied" toast.
     */
    private var loadGeneration = 0

    init {
        // Project only the prefs fields the editor mirrors into its own
        // state, then distinct on the projection: edits to unobserved
        // fields (file counter on every save, filename prefix, export
        // background, FAB flags) no longer wake this collector or run
        // the EditorState.copy(...) below.
        viewModelScope.launch {
            repository.settings
                .map { p ->
                    EditorPrefsView(
                        amoledThreshold = p.amoledThreshold,
                        amoledWarmMode  = p.amoledWarmMode,
                        rectX           = p.rectX,
                        rectY           = p.rectY,
                        rectWidth       = p.rectWidth,
                        rectHeight      = p.rectHeight,
                        rectRotated     = p.rectRotated,
                    )
                }
                .distinctUntilChanged()
                .collect { v ->
                    _state.update {
                        it.copy(
                            amoledThreshold = v.amoledThreshold,
                            amoledWarmMode  = v.amoledWarmMode,
                            rectX           = v.rectX,
                            rectY           = v.rectY,
                            rectWidth       = v.rectWidth,
                            rectHeight      = v.rectHeight,
                            rectRotated     = v.rectRotated,
                        )
                    }
                }
        }
    }

    private data class EditorPrefsView(
        val amoledThreshold: Int,
        val amoledWarmMode: Boolean,
        val rectX: Float,
        val rectY: Float,
        val rectWidth: Int,
        val rectHeight: Int,
        val rectRotated: Boolean,
    )

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
        val generation = ++loadGeneration
        viewModelScope.launch {
            val p = repository.settings.first()
            _sourceBitmap.value = null
            _analysisBitmap.value = null
            _state.update {
                it.copy(
                    rectX             = p.rectX,
                    rectY             = p.rectY,
                    rectWidth         = p.rectWidth,
                    rectHeight        = p.rectHeight,
                    rectRotated       = p.rectRotated,
                    rectVisible       = false,
                    rectBlinkPulse    = 0,
                    zoomScale         = 1f,
                    zoomOffset        = Offset.Zero,
                    // canvasSize: by omission, preserved (layout-sourced).
                    amoledThreshold   = p.amoledThreshold,
                    amoledWarmMode    = p.amoledWarmMode,
                    amoledPixelCount  = 0,
                    amoledPercent     = 0f,
                    amoledWarmNearBlackCount    = 0,
                    amoledNonWarmNearBlackCount = 0,
                    isAnalyzing       = false,
                    showAmoledOverlay = false,
                    isLoading         = true,
                    exportMessage     = null,
                    proposedFilename  = null,
                )
            }
            val loaded = runCatching { bitmapLoader.load(context, uri) }
            // A newer loadImage started while we were decoding — discard
            // this result so it can't clobber the newer image's state, and
            // recycle the orphaned bitmap (it was never published).
            if (generation != loadGeneration) {
                loaded.getOrNull()?.recycle()
                return@launch
            }
            val bitmap = loaded.getOrNull()
            _sourceBitmap.value = bitmap
            _state.update {
                it.copy(
                    isLoading = false,
                    exportMessage = when {
                        // Threw (e.g. IOException opening the stream): forward
                        // the framework message as a debugging hint.
                        loaded.isFailure ->
                            ExportMessage.Error.Generic(loaded.exceptionOrNull()?.message)
                        // Returned null without throwing (unopenable URI,
                        // zero-bounds, or a failed decode). No message to
                        // forward — surface the typed, localized error rather
                        // than leaving a blank canvas with no feedback.
                        bitmap == null -> ExportMessage.Error.CannotLoadImage
                        else -> it.exportMessage
                    },
                )
            }
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

    /**
     * Toggles the watermark-cover rectangle.
     *
     * On the ON-edge, if a source image is loaded, the canvas size is known,
     * and the persisted (rectX, rectY) falls fully inside the source image,
     * the zoom offset is set so the canvas-centered rect lands directly on
     * the stored sparkle position — and a blink pulse is fired so the user
     * spots the rect after the jump.
     *
     * If any of those preconditions fail, the rect appears in its default
     * canvas-center position and the user pans manually.
     */
    fun toggleRect() {
        _state.update { current ->
            if (current.rectVisible) current.copy(rectVisible = false)
            else current.withRectPlaced()
        }
    }

    /**
     * Turns the cover-up rect ON, jumping the canvas-pan so the rect lands
     * directly over the persisted sparkle position (rectX/rectY) when that
     * position lies fully inside the loaded source image — and fires a blink
     * pulse so the user spots the rect after the jump.
     *
     * If a source image is missing, the canvas size is unknown, or the
     * stored position falls outside the image, the rect appears at its
     * default canvas-center position (no pan, no pulse) and the user pans
     * manually.
     *
     * Shared by [toggleRect] (manual ◇ toggle) and [applyQuickAction] (the
     * ⚡ one-tap action) so both place the rect identically. Keeping this in
     * one place is the whole point: an earlier copy in [applyQuickAction]
     * set `rectVisible = true` directly and skipped the jump, so the Quick
     * Action placed the rect but never moved the image onto the sparkle.
     */
    private fun EditorState.withRectPlaced(): EditorState {
        val src = _sourceBitmap.value ?: return copy(rectVisible = true)
        val canvas = canvasSize
        if (canvas == Size.Zero) return copy(rectVisible = true)

        val tx = rectX
        val ty = rectY
        val inBounds = tx >= 0f && ty >= 0f &&
            tx + rectWidth <= src.width &&
            ty + rectHeight <= src.height
        if (!inBounds) return copy(rectVisible = true)

        val offset = ImageGeometry.computeZoomOffsetForRectAt(
            imageWidth = src.width,
            imageHeight = src.height,
            canvasWidth = canvas.width,
            canvasHeight = canvas.height,
            rectWidth = rectWidth,
            rectHeight = rectHeight,
            targetImageX = tx,
            targetImageY = ty,
            zoomScale = zoomScale,
        )
        return copy(
            rectVisible = true,
            zoomOffset = Offset(offset.x, offset.y),
            rectBlinkPulse = rectBlinkPulse + 1,
        )
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
        if (_state.value.isAnalyzing) return
        val src = _sourceBitmap.value ?: return
        val generation = loadGeneration
        viewModelScope.launch {
            val p = repository.settings.first()
            _state.update { it.copy(isAnalyzing = true) }
            var current = src
            if (p.fabApplyAmoled) {
                current = try {
                    withContext(Dispatchers.IO) {
                        ImageProcessing.applyAmoledCorrection(current, p.amoledThreshold, p.amoledWarmMode)
                    }
                } catch (e: Exception) {
                    // Clear the spinner and surface the failure instead of
                    // leaving isAnalyzing stuck true forever (dead FAB).
                    _state.update {
                        it.copy(isAnalyzing = false, exportMessage = ExportMessage.Error.Generic(e.message))
                    }
                    return@launch
                }
            }
            // A newer image was loaded mid-correction — this result is stale.
            // Recycle the freshly created bitmap (never published); leave the
            // original `src` alone (the loader/new load owns its lifecycle).
            if (generation != loadGeneration) {
                if (current !== src) current.recycle()
                return@launch
            }
            _sourceBitmap.value = current
            _state.update {
                // Apply the rect dimensions first so the jump's bounds check
                // and offset math (in withRectPlaced) use the prefs values.
                val base = it.copy(
                    isAnalyzing      = false,
                    rectWidth        = p.rectWidth,
                    rectHeight       = p.rectHeight,
                    proposedFilename = nextFilename(p.fileCounter, p.filenamePrefix),
                )
                // fabPlaceRect ON: place the rect AND jump the image onto the
                // stored sparkle position, exactly like the manual toggle.
                // OFF: leave the rect hidden and the pan untouched.
                if (p.fabPlaceRect) base.withRectPlaced() else base.copy(rectVisible = false)
            }
        }
    }

    fun setAmoledThreshold(value: Int) {
        _analysisBitmap.value = null
        _state.update {
            it.copy(
                amoledThreshold             = value,
                showAmoledOverlay           = false,
                amoledPixelCount            = 0,
                amoledPercent               = 0f,
                amoledWarmNearBlackCount    = 0,
                amoledNonWarmNearBlackCount = 0,
            )
        }
        viewModelScope.launch { repository.setAmoledThreshold(value) }
    }

    fun toggleAmoledWarmMode() {
        val newValue = !_state.value.amoledWarmMode
        _analysisBitmap.value = null
        _state.update {
            it.copy(
                amoledWarmMode              = newValue,
                showAmoledOverlay           = false,
                amoledPixelCount            = 0,
                amoledPercent               = 0f,
                amoledWarmNearBlackCount    = 0,
                amoledNonWarmNearBlackCount = 0,
            )
        }
        viewModelScope.launch { repository.setAmoledWarmMode(newValue) }
    }

    fun analyzeAmoled() {
        if (_state.value.isAnalyzing) return
        val src = _sourceBitmap.value ?: return
        val threshold = _state.value.amoledThreshold
        val warmMode = _state.value.amoledWarmMode
        val generation = loadGeneration
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val analysis = try {
                withContext(Dispatchers.IO) {
                    ImageProcessing.analyzeAmoled(src, threshold, warmMode)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isAnalyzing = false, exportMessage = ExportMessage.Error.Generic(e.message))
                }
                return@launch
            }
            // A newer image was loaded mid-analysis — discard the overlay
            // and recycle it (never published).
            if (generation != loadGeneration) {
                analysis.bitmap.recycle()
                return@launch
            }
            _analysisBitmap.value = analysis.bitmap
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    amoledPixelCount  = analysis.nearBlackCount,
                    // Long multiplication: src.width * src.height as Int
                    // overflows around 46341×46341 (=Int.MAX_VALUE), which
                    // a desktop screenshot or astro photo can realistically
                    // exceed. The Long product fits any conceivable image.
                    amoledPercent     = analysis.nearBlackCount.toFloat() /
                        (src.width.toLong() * src.height) * 100f,
                    amoledWarmNearBlackCount    = analysis.warmNearBlackCount,
                    amoledNonWarmNearBlackCount = analysis.nonWarmNearBlackCount,
                    showAmoledOverlay = true,
                )
            }
        }
    }

    fun applyAmoledCorrection() {
        if (_state.value.isAnalyzing) return
        val src = _sourceBitmap.value ?: return
        val generation = loadGeneration
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }
            val corrected = try {
                withContext(Dispatchers.IO) {
                    ImageProcessing.applyAmoledCorrection(
                        src,
                        _state.value.amoledThreshold,
                        _state.value.amoledWarmMode,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isAnalyzing = false, exportMessage = ExportMessage.Error.Generic(e.message))
                }
                return@launch
            }
            // A newer image was loaded mid-correction — discard and recycle
            // the orphaned result (never published).
            if (generation != loadGeneration) {
                corrected.recycle()
                return@launch
            }
            _sourceBitmap.value = corrected
            _analysisBitmap.value = null
            _state.update {
                it.copy(
                    isAnalyzing       = false,
                    showAmoledOverlay = false,
                    amoledPixelCount  = 0,
                    amoledPercent     = 0f,
                    amoledWarmNearBlackCount    = 0,
                    amoledNonWarmNearBlackCount = 0,
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
                amoledWarmNearBlackCount    = 0,
                amoledNonWarmNearBlackCount = 0,
            )
        }
    }

    fun saveTransparent(context: Context, uri: Uri) {
        val src = _sourceBitmap.value ?: return
        val s = _state.value
        viewModelScope.launch {
            val background = repository.settings.first().exportBackground
            val message: ExportMessage = withContext(Dispatchers.IO) {
                // The PNG write is the operation the user actually cares
                // about; calibration writes (rect origin, counter bump)
                // are bookkeeping that runs only on success. If the PNG
                // wrote but DataStore later fails, we still report Saved
                // — the file is on disk; a missed counter bump is the
                // worst-case symptom, not a misleading "Error" toast.
                runCatching { writeExport(context, uri, src, s, background) }.fold(
                    onSuccess = { origin ->
                        runCatching {
                            if (origin != null) {
                                // Persist the rect's effective image-space
                                // position so the next image load can jump
                                // straight to it. The defaults (683/1291)
                                // are a guess; this turns every successful
                                // export into a calibration step.
                                repository.setRectX(origin.x.toFloat())
                                repository.setRectY(origin.y.toFloat())
                            }
                            repository.incrementCounter()
                        }
                        ExportMessage.Saved
                    },
                    onFailure = { e ->
                        when (e) {
                            is CannotOpenOutputStreamException -> ExportMessage.Error.CannotOpenOutputStream
                            is CanvasNotReadyException -> ExportMessage.Error.CanvasNotReady
                            else -> ExportMessage.Error.Generic(e.message)
                        }
                    },
                )
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
     * Clears the auto-set [EditorState.proposedFilename] after the save
     * dialog is dismissed without writing a file.
     *
     * Why this exists: [applyQuickAction] sets [EditorState.proposedFilename]
     * to drive a `LaunchedEffect`-keyed launch of the SAF CreateDocument
     * picker. If the user cancels that picker (back press, swipe down on
     * the sheet), the launcher callback receives `uri = null` and we never
     * enter [saveTransparent] — which is the only other path that nulls
     * the field. Without this method the field would stay non-null, the
     * `LaunchedEffect` would not re-fire on the next Quick-Action with the
     * *same* suggested filename (e.g. because the file counter hasn't
     * advanced), and the FAB would appear to do nothing.
     */
    fun clearProposedFilename() {
        _state.update { it.copy(proposedFilename = null) }
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
     *
     * Returns the rect's image-space top-left origin when the rect was
     * drawn (so the caller can persist it for cross-image position memory),
     * or null when the rect was hidden.
     */
    private fun writeExport(
        context: Context,
        uri: Uri,
        src: Bitmap,
        s: EditorState,
        background: ExportBackground,
    ): RectOrigin? {
        // The rect-export branch reads canvasSize to map the canvas-centered
        // rect into image-space coordinates. Without a laid-out canvas the
        // export would silently drop the rect; surface it as a typed error
        // instead. Checked before the bitmap copy so a no-op save costs
        // nothing.
        if (s.rectVisible && s.canvasSize == Size.Zero) throw CanvasNotReadyException()
        val working = src.copy(Bitmap.Config.ARGB_8888, true)
        try {
            val origin: RectOrigin? = if (s.rectVisible) {
                val o = ImageGeometry.computeRectOriginInImage(
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
                ImageProcessing.drawBlackRect(working, o.x, o.y, s.rectWidth, s.rectHeight, s.rectRotated)
                o
            } else null
            // result === working in AMOLED mode; a fresh bitmap in TRANSPARENT
            // mode. The release path below recycles `result` first, then
            // `working` only if it is a distinct allocation — otherwise we'd
            // double-free.
            val result = when (background) {
                ExportBackground.AMOLED -> working
                ExportBackground.TRANSPARENT -> ImageProcessing.blackToTransparent(working)
            }
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    result.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: throw CannotOpenOutputStreamException()
            } finally {
                if (result !== working) result.recycle()
            }
            return origin
        } finally {
            working.recycle()
        }
    }

    /**
     * Marker exception so [saveTransparent]'s `getOrElse` can distinguish
     * the one failure we can name precisely (and therefore localize) from
     * arbitrary framework exceptions. Private to the file because it
     * crosses no module boundary — the sealed [ExportMessage.Error]
     * hierarchy is what the UI sees.
     */
    private class CannotOpenOutputStreamException :
        IOException("Cannot open output stream")

    /** See [ExportMessage.Error.CanvasNotReady] for the user-facing meaning. */
    private class CanvasNotReadyException :
        IllegalStateException("Canvas not laid out yet")

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
