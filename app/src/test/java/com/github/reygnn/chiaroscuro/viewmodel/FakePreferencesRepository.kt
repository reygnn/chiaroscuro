package com.github.reygnn.chiaroscuro.viewmodel

import com.github.reygnn.chiaroscuro.preferences.PreferencesRepository
import com.github.reygnn.chiaroscuro.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [PreferencesRepository] for ViewModel tests.
 *
 * Lives in the test source set and implements the interface faithfully
 * (clamping, distinctness, immediate emission) so ViewModel tests
 * exercise the same contract production code does.
 *
 * We deliberately do NOT use mockk here: constructing a relaxed mock
 * for a Flow-returning repository tends to produce stale emissions
 * and brittle ordering assertions. A hand-rolled fake keeps tests
 * readable and the semantics deterministic.
 */
internal class FakePreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : PreferencesRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<UserPreferences> = _settings.asStateFlow()

    val current: UserPreferences get() = _settings.value

    override suspend fun setAmoledThreshold(value: Int) {
        _settings.value = _settings.value.copy(
            amoledThreshold = value.coerceIn(
                UserPreferences.AMOLED_THRESHOLD_MIN,
                UserPreferences.AMOLED_THRESHOLD_MAX,
            ),
        )
    }

    override suspend fun setAmoledWarmMode(enabled: Boolean) {
        _settings.value = _settings.value.copy(amoledWarmMode = enabled)
    }

    override suspend fun setFabApplyAmoled(enabled: Boolean) {
        _settings.value = _settings.value.copy(fabApplyAmoled = enabled)
    }

    override suspend fun setFabPlaceRect(enabled: Boolean) {
        _settings.value = _settings.value.copy(fabPlaceRect = enabled)
    }

    override suspend fun setRectX(value: Float) {
        _settings.value = _settings.value.copy(rectX = value)
    }

    override suspend fun setRectY(value: Float) {
        _settings.value = _settings.value.copy(rectY = value)
    }

    override suspend fun setRectWidth(value: Int) {
        _settings.value = _settings.value.copy(
            rectWidth = value.coerceAtLeast(UserPreferences.RECT_SIZE_MIN),
        )
    }

    override suspend fun setRectHeight(value: Int) {
        _settings.value = _settings.value.copy(
            rectHeight = value.coerceAtLeast(UserPreferences.RECT_SIZE_MIN),
        )
    }

    override suspend fun setCounter(value: Int) {
        _settings.value = _settings.value.copy(
            sleeveCounter = value.coerceAtLeast(UserPreferences.SLEEVE_COUNTER_MIN),
        )
    }

    override suspend fun incrementCounter() {
        _settings.value = _settings.value.copy(
            sleeveCounter = _settings.value.sleeveCounter + 1,
        )
    }

    override suspend fun resetCounter() {
        _settings.value = _settings.value.copy(
            sleeveCounter = UserPreferences.SLEEVE_COUNTER_MIN,
        )
    }

    override suspend fun setFilenamePrefix(value: String) {
        _settings.value = _settings.value.copy(
            filenamePrefix = value.ifBlank { UserPreferences.DEFAULT_FILENAME_PREFIX },
        )
    }
}