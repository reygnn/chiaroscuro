package com.github.reygnn.chiaroscuro.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Minimal in-memory [DataStore]<[Preferences]> for unit tests.
 *
 * Implements the two semantics this project relies on:
 *   - `data` is a hot Flow that emits the current snapshot on subscription
 *     and every successful edit.
 *   - `updateData` applies the transform atomically under a mutex, matching
 *     the real DataStore's serialization guarantee.
 *
 * Does NOT implement: file persistence, corruption handling, migrations,
 * cancellation semantics. We don't need them here.
 */
internal class FakeDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val current = state.value
        val mutable: MutablePreferences = mutablePreferencesOf().apply {
            putAll(*current.asMap().entries
                .map { (k, v) ->
                    @Suppress("UNCHECKED_CAST")
                    (k as Preferences.Key<Any>) to v
                }
                .toTypedArray())
        }
        val next = transform(mutable).toPreferences()
        state.value = next
        next
    }
}