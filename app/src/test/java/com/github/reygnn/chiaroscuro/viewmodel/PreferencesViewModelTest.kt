package com.github.reygnn.chiaroscuro.viewmodel

import app.cash.turbine.test
import com.github.reygnn.chiaroscuro.preferences.ExportBackground
import com.github.reygnn.chiaroscuro.preferences.UserPreferences
import com.github.reygnn.chiaroscuro.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [PreferencesViewModel].
 *
 * IMPORTANT — do NOT promote repository/vm to test-class fields.
 * [MainDispatcherRule] installs the test dispatcher as Dispatchers.Main
 * in @Rule setup, which runs AFTER field initializers. A VM constructed
 * as a field binds its viewModelScope to the real Main, whose work
 * never runs under a JVM test — advanceUntilIdle() then drains the
 * wrong scheduler. Construct both inside runTest(mainRule.testDispatcher).
 */
class PreferencesViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `setAmoledThreshold forwards to repository`() = runTest(mainRule.testDispatcher) {
        val repository = FakePreferencesRepository()
        val vm = PreferencesViewModel(repository)

        vm.setAmoledThreshold(25)
        advanceUntilIdle()

        assertEquals(25, repository.current.amoledThreshold)
    }

    @Test
    fun `setAmoledWarmMode forwards to repository`() = runTest(mainRule.testDispatcher) {
        val repository = FakePreferencesRepository()
        val vm = PreferencesViewModel(repository)

        vm.setAmoledWarmMode(true)
        advanceUntilIdle()

        assertTrue(repository.current.amoledWarmMode)
    }

    @Test
    fun `setFabApplyAmoled and setFabPlaceRect forward to repository`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = PreferencesViewModel(repository)

            vm.setFabApplyAmoled(false)
            vm.setFabPlaceRect(false)
            advanceUntilIdle()

            assertFalse(repository.current.fabApplyAmoled)
            assertFalse(repository.current.fabPlaceRect)
        }

    @Test
    fun `rect setters forward to repository`() = runTest(mainRule.testDispatcher) {
        val repository = FakePreferencesRepository()
        val vm = PreferencesViewModel(repository)

        vm.setRectX(100.5f)
        vm.setRectY(200.5f)
        vm.setRectWidth(80)
        vm.setRectHeight(90)
        advanceUntilIdle()

        val prefs = repository.current
        assertEquals(100.5f, prefs.rectX)
        assertEquals(200.5f, prefs.rectY)
        assertEquals(80, prefs.rectWidth)
        assertEquals(90, prefs.rectHeight)
    }

    @Test
    fun `counter setters forward to repository`() = runTest(mainRule.testDispatcher) {
        val repository = FakePreferencesRepository()
        val vm = PreferencesViewModel(repository)

        vm.setCounter(42)
        advanceUntilIdle()
        assertEquals(42, repository.current.sleeveCounter)

        vm.resetCounter()
        advanceUntilIdle()
        assertEquals(UserPreferences.SLEEVE_COUNTER_MIN, repository.current.sleeveCounter)
    }

    @Test
    fun `setFilenamePrefix forwards to repository`() = runTest(mainRule.testDispatcher) {
        val repository = FakePreferencesRepository()
        val vm = PreferencesViewModel(repository)

        vm.setFilenamePrefix("cover")
        advanceUntilIdle()

        assertEquals("cover", repository.current.filenamePrefix)
    }

    @Test
    fun `appPrefs reflects repository state after a change`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = PreferencesViewModel(repository)

            vm.appPrefs.test {
                // Initial emit from stateIn's initialValue = UserPreferences()
                assertEquals(UserPreferences(), awaitItem())

                vm.setAmoledThreshold(30)
                advanceUntilIdle()

                assertEquals(30, awaitItem().amoledThreshold)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `repository clamping is visible through appPrefs`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = PreferencesViewModel(repository)

            vm.setAmoledThreshold(UserPreferences.AMOLED_THRESHOLD_MAX + 1000)
            advanceUntilIdle()

            assertEquals(
                UserPreferences.AMOLED_THRESHOLD_MAX,
                repository.current.amoledThreshold,
            )
        }

    @Test
    fun `setExportBackgroundTransparent forwards to repository`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = PreferencesViewModel(repository)

            vm.setExportBackgroundTransparent(true)
            advanceUntilIdle()
            assertEquals(ExportBackground.TRANSPARENT, repository.current.exportBackground)

            vm.setExportBackgroundTransparent(false)
            advanceUntilIdle()
            assertEquals(ExportBackground.AMOLED, repository.current.exportBackground)
        }
}