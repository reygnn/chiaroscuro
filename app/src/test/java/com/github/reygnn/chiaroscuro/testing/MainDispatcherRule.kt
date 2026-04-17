package com.github.reygnn.chiaroscuro.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that installs a single [TestDispatcher] as Main for the
 * duration of a test, and tears it down afterwards.
 *
 * Convention for this project (see TESTING_CONVENTIONS.kt):
 *   - Tests share **one** dispatcher through this rule.
 *   - Tests never instantiate their own TestScope or StandardTestDispatcher.
 *   - Virtual-time control (advanceUntilIdle / advanceTimeBy / runCurrent)
 *     is done via `runTest(rule.testDispatcher)` in each test.
 *
 * This keeps dispatcher identity consistent between the code under test
 * (which uses Dispatchers.Main via viewModelScope) and the test body,
 * so that `advanceUntilIdle()` actually drains the work that the VM
 * scheduled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}