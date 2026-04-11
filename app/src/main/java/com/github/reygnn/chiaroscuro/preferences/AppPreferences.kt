package com.github.reygnn.chiaroscuro.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chiaroscuro_prefs")

data class AppPrefs(
    // AMOLED
    val amoledThreshold: Int     = 50,
    val amoledWarmMode: Boolean  = false,
    // FAB Steps
    val fabApplyAmoled: Boolean  = true,
    val fabPlaceRect: Boolean    = true,
    // Rectangle
    val rectX: Float             = 683f,
    val rectY: Float             = 1291f,
    val rectWidth: Int           = 57,
    val rectHeight: Int          = 57,
    // Sleeve counter
    val sleeveCounter: Int       = 1
)

object PrefsKeys {
    val AMOLED_THRESHOLD  = intPreferencesKey("amoled_threshold")
    val AMOLED_WARM_MODE  = booleanPreferencesKey("amoled_warm_mode")
    val FAB_APPLY_AMOLED  = booleanPreferencesKey("fab_apply_amoled")
    val FAB_PLACE_RECT    = booleanPreferencesKey("fab_place_rect")
    val RECT_X            = floatPreferencesKey("rect_x")
    val RECT_Y            = floatPreferencesKey("rect_y")
    val RECT_WIDTH        = intPreferencesKey("rect_width")
    val RECT_HEIGHT       = intPreferencesKey("rect_height")
    val SLEEVE_COUNTER    = intPreferencesKey("sleeve_counter")
}

class AppPreferences(private val context: Context) {

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p ->
        AppPrefs(
            amoledThreshold = p[PrefsKeys.AMOLED_THRESHOLD] ?: 50,
            amoledWarmMode  = p[PrefsKeys.AMOLED_WARM_MODE]  ?: false,
            fabApplyAmoled  = p[PrefsKeys.FAB_APPLY_AMOLED]  ?: true,
            fabPlaceRect    = p[PrefsKeys.FAB_PLACE_RECT]    ?: true,
            rectX           = p[PrefsKeys.RECT_X]            ?: 683f,
            rectY           = p[PrefsKeys.RECT_Y]            ?: 1291f,
            rectWidth       = p[PrefsKeys.RECT_WIDTH]        ?: 57,
            rectHeight      = p[PrefsKeys.RECT_HEIGHT]       ?: 57,
            sleeveCounter   = p[PrefsKeys.SLEEVE_COUNTER]    ?: 1
        )
    }

    suspend fun setAmoledThreshold(v: Int)    = context.dataStore.edit { it[PrefsKeys.AMOLED_THRESHOLD] = v }
    suspend fun setAmoledWarmMode(v: Boolean) = context.dataStore.edit { it[PrefsKeys.AMOLED_WARM_MODE]  = v }
    suspend fun setFabApplyAmoled(v: Boolean) = context.dataStore.edit { it[PrefsKeys.FAB_APPLY_AMOLED]  = v }
    suspend fun setFabPlaceRect(v: Boolean)   = context.dataStore.edit { it[PrefsKeys.FAB_PLACE_RECT]    = v }
    suspend fun setRectX(v: Float)            = context.dataStore.edit { it[PrefsKeys.RECT_X]            = v }
    suspend fun setRectY(v: Float)            = context.dataStore.edit { it[PrefsKeys.RECT_Y]            = v }
    suspend fun setRectWidth(v: Int)          = context.dataStore.edit { it[PrefsKeys.RECT_WIDTH]        = v }
    suspend fun setRectHeight(v: Int)         = context.dataStore.edit { it[PrefsKeys.RECT_HEIGHT]       = v }
    suspend fun incrementCounter()            = context.dataStore.edit { it[PrefsKeys.SLEEVE_COUNTER]    = (it[PrefsKeys.SLEEVE_COUNTER] ?: 1) + 1 }
    suspend fun resetCounter()                = context.dataStore.edit { it[PrefsKeys.SLEEVE_COUNTER]    = 1 }
}