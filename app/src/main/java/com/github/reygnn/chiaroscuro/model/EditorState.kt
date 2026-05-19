package com.github.reygnn.chiaroscuro.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_AMOLED_THRESHOLD
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_AMOLED_WARM_MODE
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_HEIGHT
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_ROTATED
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_WIDTH
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_X
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_Y

/**
 * Transient UI state for the editor.
 *
 * Every field that is also persisted ([rectX], [rectY], [rectWidth],
 * [rectHeight], [rectRotated], [amoledThreshold], [amoledWarmMode]) defaults
 * to the corresponding [com.github.reygnn.chiaroscuro.preferences.UserPreferences]
 * `DEFAULT_*` constant so the first-frame UI — rendered before the
 * repository's `settings` Flow has emitted — agrees with the values the
 * collect will deliver one frame later. Hand-typed numeric defaults here
 * would visibly disagree with the persisted state for that single frame
 * AND would drift out of sync the next time anyone changed a default in
 * only one of the two files.
 */
data class EditorState(
    val rectX: Float = DEFAULT_RECT_X,
    val rectY: Float = DEFAULT_RECT_Y,
    val rectWidth: Int = DEFAULT_RECT_WIDTH,
    val rectHeight: Int = DEFAULT_RECT_HEIGHT,
    val rectRotated: Boolean = DEFAULT_RECT_ROTATED,
    val rectVisible: Boolean = false,
    /**
     * Incremented whenever the editor wants the preview rect to blink — e.g.
     * after a position-jump on toggle-on. ImageCanvas observes this and runs
     * a brief alpha-pulse animation. Pure counter; the value itself is
     * meaningless, only its change matters.
     */
    val rectBlinkPulse: Int = 0,
    val zoomScale: Float = 1f,
    val zoomOffset: Offset = Offset.Zero,
    val canvasSize: Size = Size.Zero,
    val amoledThreshold: Int = DEFAULT_AMOLED_THRESHOLD,
    val amoledWarmMode: Boolean = DEFAULT_AMOLED_WARM_MODE,
    val amoledPixelCount: Int = 0,
    val amoledPercent: Float = 0f,
    val isAnalyzing: Boolean = false,
    val showAmoledOverlay: Boolean = false,
    val isLoading: Boolean = false,
    val exportMessage: ExportMessage? = null,
    val proposedFilename: String? = null,
)