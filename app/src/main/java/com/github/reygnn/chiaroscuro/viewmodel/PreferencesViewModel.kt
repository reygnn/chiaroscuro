package com.github.reygnn.chiaroscuro.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.reygnn.chiaroscuro.ChiaroscuroApplication
import com.github.reygnn.chiaroscuro.preferences.ExportBackground
import com.github.reygnn.chiaroscuro.preferences.PreferencesRepository
import com.github.reygnn.chiaroscuro.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(
    private val repository: PreferencesRepository,
) : ViewModel() {

    val appPrefs: StateFlow<UserPreferences> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = UserPreferences(),
    )

    fun setAmoledThreshold(value: Int) =
        viewModelScope.launch { repository.setAmoledThreshold(value) }

    fun setAmoledWarmMode(enabled: Boolean) =
        viewModelScope.launch { repository.setAmoledWarmMode(enabled) }

    fun setFabApplyAmoled(enabled: Boolean) =
        viewModelScope.launch { repository.setFabApplyAmoled(enabled) }

    fun setFabPlaceRect(enabled: Boolean) =
        viewModelScope.launch { repository.setFabPlaceRect(enabled) }

    fun setRectX(value: Float) =
        viewModelScope.launch { repository.setRectX(value) }

    fun setRectY(value: Float) =
        viewModelScope.launch { repository.setRectY(value) }

    fun setRectWidth(value: Int) =
        viewModelScope.launch { repository.setRectWidth(value) }

    fun setRectHeight(value: Int) =
        viewModelScope.launch { repository.setRectHeight(value) }

    fun setCounter(value: Int) =
        viewModelScope.launch { repository.setCounter(value) }

    fun resetCounter() =
        viewModelScope.launch { repository.resetCounter() }

    fun setFilenamePrefix(value: String) =
        viewModelScope.launch { repository.setFilenamePrefix(value) }

    fun setExportBackgroundTransparent(enabled: Boolean) =
        viewModelScope.launch {
            repository.setExportBackground(
                if (enabled) ExportBackground.TRANSPARENT else ExportBackground.AMOLED,
            )
        }

    companion object {
        private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ChiaroscuroApplication
                PreferencesViewModel(app.preferencesRepository)
            }
        }
    }
}