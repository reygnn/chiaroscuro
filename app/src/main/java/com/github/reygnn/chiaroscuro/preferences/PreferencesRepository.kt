package com.github.reygnn.chiaroscuro.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for persisted user preferences.
 *
 * Implementations must make the [settings] Flow conflating / deduplicating
 * so that unchanged values do not trigger spurious collector updates, and
 * setter functions must clamp inputs to valid ranges where a range is
 * defined in [UserPreferences.Companion]. This contract is exercised by
 * the repository tests; ViewModels rely on it.
 */
interface PreferencesRepository {

    val settings: Flow<UserPreferences>

    suspend fun setAmoledThreshold(value: Int)
    suspend fun setAmoledWarmMode(enabled: Boolean)

    suspend fun setFabApplyAmoled(enabled: Boolean)
    suspend fun setFabPlaceRect(enabled: Boolean)

    suspend fun setRectX(value: Float)
    suspend fun setRectY(value: Float)
    suspend fun setRectWidth(value: Int)
    suspend fun setRectHeight(value: Int)

    suspend fun setCounter(value: Int)
    suspend fun incrementCounter()
    suspend fun resetCounter()

    suspend fun setFilenamePrefix(value: String)
}