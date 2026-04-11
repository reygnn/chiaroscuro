package com.github.reygnn.chiaroscuro.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.reygnn.chiaroscuro.preferences.AppPreferences
import com.github.reygnn.chiaroscuro.preferences.AppPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PreferencesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)

    val appPrefs = prefs.prefs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Companion.WhileSubscribed(5_000),
        initialValue = AppPrefs()
    )

    fun setAmoledThreshold(v: Int)    = viewModelScope.launch { prefs.setAmoledThreshold(v) }
    fun setAmoledWarmMode(v: Boolean) = viewModelScope.launch { prefs.setAmoledWarmMode(v) }
    fun setFabApplyAmoled(v: Boolean) = viewModelScope.launch { prefs.setFabApplyAmoled(v) }
    fun setFabPlaceRect(v: Boolean)   = viewModelScope.launch { prefs.setFabPlaceRect(v) }
    fun setRectX(v: Float)            = viewModelScope.launch { prefs.setRectX(v) }
    fun setRectY(v: Float)            = viewModelScope.launch { prefs.setRectY(v) }
    fun setRectWidth(v: Int)          = viewModelScope.launch { prefs.setRectWidth(v) }
    fun setRectHeight(v: Int)         = viewModelScope.launch { prefs.setRectHeight(v) }
    fun resetCounter()                = viewModelScope.launch { prefs.resetCounter() }
    fun setCounter(v: Int)            = viewModelScope.launch { prefs.setCounter(v) }
    fun setFilenamePrefix(v: String) = viewModelScope.launch { prefs.setFilenamePrefix(v) }
}