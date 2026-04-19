package com.github.reygnn.chiaroscuro.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.AMOLED_THRESHOLD_MAX
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.AMOLED_THRESHOLD_MIN
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_AMOLED_THRESHOLD
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_AMOLED_WARM_MODE
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_EXPORT_BACKGROUND
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_FAB_APPLY_AMOLED
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_FAB_PLACE_RECT
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_FILENAME_PREFIX
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_HEIGHT
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_WIDTH
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_X
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_RECT_Y
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_SLEEVE_COUNTER
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.RECT_SIZE_MIN
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.SLEEVE_COUNTER_MIN
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed implementation of [PreferencesRepository].
 *
 * Keys and their default values are private to this file. Callers speak
 * only [UserPreferences] — the on-disk schema is an internal concern.
 *
 * [ExportBackground] is persisted as its enum name string. Unknown or
 * corrupted values fall back to [DEFAULT_EXPORT_BACKGROUND] rather than
 * throwing; DataStore corruption is rare but we never want a single bad
 * write to crash the app on launch.
 */
internal class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val settings: Flow<UserPreferences> = dataStore.data
        .map { p ->
            UserPreferences(
                amoledThreshold  = p[Keys.AMOLED_THRESHOLD]  ?: DEFAULT_AMOLED_THRESHOLD,
                amoledWarmMode   = p[Keys.AMOLED_WARM_MODE]  ?: DEFAULT_AMOLED_WARM_MODE,
                fabApplyAmoled   = p[Keys.FAB_APPLY_AMOLED]  ?: DEFAULT_FAB_APPLY_AMOLED,
                fabPlaceRect     = p[Keys.FAB_PLACE_RECT]    ?: DEFAULT_FAB_PLACE_RECT,
                rectX            = p[Keys.RECT_X]            ?: DEFAULT_RECT_X,
                rectY            = p[Keys.RECT_Y]            ?: DEFAULT_RECT_Y,
                rectWidth        = p[Keys.RECT_WIDTH]        ?: DEFAULT_RECT_WIDTH,
                rectHeight       = p[Keys.RECT_HEIGHT]       ?: DEFAULT_RECT_HEIGHT,
                sleeveCounter    = p[Keys.SLEEVE_COUNTER]    ?: DEFAULT_SLEEVE_COUNTER,
                filenamePrefix   = p[Keys.FILENAME_PREFIX]   ?: DEFAULT_FILENAME_PREFIX,
                exportBackground = p[Keys.EXPORT_BACKGROUND]
                    ?.let { name -> runCatching { ExportBackground.valueOf(name) }.getOrNull() }
                    ?: DEFAULT_EXPORT_BACKGROUND,
            )
        }
        .distinctUntilChanged()

    override suspend fun setAmoledThreshold(value: Int) {
        dataStore.edit {
            it[Keys.AMOLED_THRESHOLD] = value.coerceIn(AMOLED_THRESHOLD_MIN, AMOLED_THRESHOLD_MAX)
        }
    }

    override suspend fun setAmoledWarmMode(enabled: Boolean) {
        dataStore.edit { it[Keys.AMOLED_WARM_MODE] = enabled }
    }

    override suspend fun setFabApplyAmoled(enabled: Boolean) {
        dataStore.edit { it[Keys.FAB_APPLY_AMOLED] = enabled }
    }

    override suspend fun setFabPlaceRect(enabled: Boolean) {
        dataStore.edit { it[Keys.FAB_PLACE_RECT] = enabled }
    }

    override suspend fun setRectX(value: Float) {
        dataStore.edit { it[Keys.RECT_X] = value }
    }

    override suspend fun setRectY(value: Float) {
        dataStore.edit { it[Keys.RECT_Y] = value }
    }

    override suspend fun setRectWidth(value: Int) {
        dataStore.edit { it[Keys.RECT_WIDTH] = value.coerceAtLeast(RECT_SIZE_MIN) }
    }

    override suspend fun setRectHeight(value: Int) {
        dataStore.edit { it[Keys.RECT_HEIGHT] = value.coerceAtLeast(RECT_SIZE_MIN) }
    }

    override suspend fun setCounter(value: Int) {
        dataStore.edit { it[Keys.SLEEVE_COUNTER] = value.coerceAtLeast(SLEEVE_COUNTER_MIN) }
    }

    override suspend fun incrementCounter() {
        dataStore.edit {
            val current = it[Keys.SLEEVE_COUNTER] ?: DEFAULT_SLEEVE_COUNTER
            it[Keys.SLEEVE_COUNTER] = current + 1
        }
    }

    override suspend fun resetCounter() {
        dataStore.edit { it[Keys.SLEEVE_COUNTER] = SLEEVE_COUNTER_MIN }
    }

    override suspend fun setFilenamePrefix(value: String) {
        dataStore.edit {
            it[Keys.FILENAME_PREFIX] = value.ifBlank { DEFAULT_FILENAME_PREFIX }
        }
    }

    override suspend fun setExportBackground(value: ExportBackground) {
        dataStore.edit { it[Keys.EXPORT_BACKGROUND] = value.name }
    }

    private object Keys {
        val AMOLED_THRESHOLD  = intPreferencesKey("amoled_threshold")
        val AMOLED_WARM_MODE  = booleanPreferencesKey("amoled_warm_mode")
        val FAB_APPLY_AMOLED  = booleanPreferencesKey("fab_apply_amoled")
        val FAB_PLACE_RECT    = booleanPreferencesKey("fab_place_rect")
        val RECT_X            = floatPreferencesKey("rect_x")
        val RECT_Y            = floatPreferencesKey("rect_y")
        val RECT_WIDTH        = intPreferencesKey("rect_width")
        val RECT_HEIGHT       = intPreferencesKey("rect_height")
        val SLEEVE_COUNTER    = intPreferencesKey("sleeve_counter")
        val FILENAME_PREFIX   = stringPreferencesKey("filename_prefix")
        val EXPORT_BACKGROUND = stringPreferencesKey("export_background")
    }
}