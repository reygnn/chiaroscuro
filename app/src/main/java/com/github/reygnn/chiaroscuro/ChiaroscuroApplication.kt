package com.github.reygnn.chiaroscuro

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.reygnn.chiaroscuro.preferences.DataStorePreferencesRepository
import com.github.reygnn.chiaroscuro.preferences.PreferencesRepository

/**
 * File-private DataStore delegate — declared exactly once across the app
 * to avoid creating multiple DataStore instances for the same file.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chiaroscuro_prefs",
)

/**
 * Application-wide object graph. Holds the single instance of
 * [PreferencesRepository] that all ViewModels share via their factories.
 *
 * Deliberately minimal — if/when a DI framework is introduced, this
 * class becomes the composition root and nothing else needs to move.
 */
class ChiaroscuroApplication : Application() {

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(dataStore)
    }
}