package com.github.reygnn.chiaroscuro.preferences

/**
 * Immutable snapshot of all persisted user preferences.
 *
 * All defaults and validation bounds are declared as public constants in the
 * companion object so that [PreferencesRepository] implementations, tests,
 * and UI can reference the same source of truth.
 */
data class UserPreferences(
    val amoledThreshold: Int    = DEFAULT_AMOLED_THRESHOLD,
    val amoledWarmMode: Boolean = DEFAULT_AMOLED_WARM_MODE,
    val fabApplyAmoled: Boolean = DEFAULT_FAB_APPLY_AMOLED,
    val fabPlaceRect: Boolean   = DEFAULT_FAB_PLACE_RECT,
    val rectX: Float            = DEFAULT_RECT_X,
    val rectY: Float            = DEFAULT_RECT_Y,
    val rectWidth: Int          = DEFAULT_RECT_WIDTH,
    val rectHeight: Int         = DEFAULT_RECT_HEIGHT,
    val sleeveCounter: Int      = DEFAULT_SLEEVE_COUNTER,
    val filenamePrefix: String  = DEFAULT_FILENAME_PREFIX,
) {
    companion object {
        const val DEFAULT_AMOLED_THRESHOLD = 50
        const val DEFAULT_AMOLED_WARM_MODE = false
        const val DEFAULT_FAB_APPLY_AMOLED = true
        const val DEFAULT_FAB_PLACE_RECT   = true
        const val DEFAULT_RECT_X           = 683f
        const val DEFAULT_RECT_Y           = 1291f
        const val DEFAULT_RECT_WIDTH       = 57
        const val DEFAULT_RECT_HEIGHT      = 57
        const val DEFAULT_SLEEVE_COUNTER   = 1
        const val DEFAULT_FILENAME_PREFIX  = "sleeve"

        const val AMOLED_THRESHOLD_MIN = 0
        const val AMOLED_THRESHOLD_MAX = 50
        const val RECT_SIZE_MIN        = 1
        const val SLEEVE_COUNTER_MIN   = 1
    }
}