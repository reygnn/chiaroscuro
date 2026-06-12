package com.github.reygnn.chiaroscuro.preferences

/**
 * Immutable snapshot of all persisted user preferences.
 *
 * All defaults and validation bounds are declared as public constants in the
 * companion object so that [PreferencesRepository] implementations, tests,
 * and UI can reference the same source of truth.
 */
data class UserPreferences(
    val amoledThreshold: Int         = DEFAULT_AMOLED_THRESHOLD,
    val amoledWarmMode: Boolean      = DEFAULT_AMOLED_WARM_MODE,
    val amoledPerceptual: Boolean    = DEFAULT_AMOLED_PERCEPTUAL,
    val fabApplyAmoled: Boolean      = DEFAULT_FAB_APPLY_AMOLED,
    val fabPlaceRect: Boolean        = DEFAULT_FAB_PLACE_RECT,
    val rectX: Float                 = DEFAULT_RECT_X,
    val rectY: Float                 = DEFAULT_RECT_Y,
    val rectWidth: Int               = DEFAULT_RECT_WIDTH,
    val rectHeight: Int              = DEFAULT_RECT_HEIGHT,
    val rectRotated: Boolean         = DEFAULT_RECT_ROTATED,
    val fileCounter: Int             = DEFAULT_FILE_COUNTER,
    val filenamePrefix: String       = DEFAULT_FILENAME_PREFIX,
    val exportBackground: ExportBackground = DEFAULT_EXPORT_BACKGROUND,
) {
    companion object {
        const val DEFAULT_AMOLED_THRESHOLD = 50
        const val DEFAULT_AMOLED_WARM_MODE = false

        // Perceptual (luminance) detection is the default: it removes the
        // colored near-black that the per-channel rule structurally misses,
        // i.e. maximum fake-AMOLED cleanup. Set false to opt back into the
        // hue-preserving per-channel rule.
        const val DEFAULT_AMOLED_PERCEPTUAL = true
        const val DEFAULT_FAB_APPLY_AMOLED = true
        const val DEFAULT_FAB_PLACE_RECT   = true
        const val DEFAULT_RECT_X           = 683f
        const val DEFAULT_RECT_Y           = 1291f
        const val DEFAULT_RECT_WIDTH       = 57
        const val DEFAULT_RECT_HEIGHT      = 57
        const val DEFAULT_RECT_ROTATED     = true
        const val DEFAULT_FILE_COUNTER     = 1
        const val DEFAULT_FILENAME_PREFIX  = "image"
        val DEFAULT_EXPORT_BACKGROUND      = ExportBackground.AMOLED

        const val AMOLED_THRESHOLD_MIN = 0
        const val AMOLED_THRESHOLD_MAX = 150
        const val RECT_SIZE_MIN        = 1
        const val FILE_COUNTER_MIN     = 1
    }
}

/**
 * Controls how the export pipeline treats pure-black pixels.
 *
 * - [AMOLED] keeps them black. On AMOLED displays these render as
 *   fully off pixels already — no alpha channel work required.
 *   PNG output, fully opaque.
 * - [TRANSPARENT] replaces them with alpha=0. The image becomes
 *   a cutout that shows whatever is behind it (wallpaper, another
 *   layer, …). PNG output with alpha channel.
 */
enum class ExportBackground {
    AMOLED,
    TRANSPARENT,
}