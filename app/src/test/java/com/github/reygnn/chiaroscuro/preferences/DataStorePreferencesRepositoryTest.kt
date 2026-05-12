package com.github.reygnn.chiaroscuro.preferences

import app.cash.turbine.test
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.AMOLED_THRESHOLD_MAX
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.AMOLED_THRESHOLD_MIN
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.DEFAULT_FILENAME_PREFIX
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.RECT_SIZE_MIN
import com.github.reygnn.chiaroscuro.preferences.UserPreferences.Companion.SLEEVE_COUNTER_MIN
import com.github.reygnn.chiaroscuro.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Contract tests for [DataStorePreferencesRepository].
 *
 * Uses an in-memory DataStore-like fake built on top of [FakeDataStore]
 * (see test support in this package) so tests exercise the same code
 * paths as production — including the distinctUntilChanged and clamping
 * behavior — without touching disk.
 *
 * Test seams:
 *   - [FakeDataStore] emits the current value on subscription and on
 *     every edit, matching DataStore<Preferences> semantics closely
 *     enough for unit testing.
 *   - Clamping is verified via out-of-range inputs.
 *   - Flow distinctness is verified by writing the same value twice
 *     and asserting only one emission after the initial snapshot.
 *
 * Field initialisation — `@Before`, not class fields, not per-test:
 *   [MainDispatcherRule] installs its test dispatcher as
 *   [kotlinx.coroutines.Dispatchers.Main] in JUnit's `@Rule` setup
 *   phase, which runs **before** `@Before`. `dataStore` and
 *   `repository` are therefore safe to initialise in `@Before` —
 *   construction happens after the test dispatcher is installed, so
 *   any future change to `DataStorePreferencesRepository` that
 *   captures a scope or `Dispatchers.Main` at construction time
 *   binds to the test dispatcher rather than the real Main.
 *
 *   `TESTING_CONVENTIONS.kt` §2 phrases this rule as "inside
 *   `runTest`", which is stricter than the underlying hazard requires
 *   — `@Before` runs after `@Rule.starting()` and before `@Test`, so
 *   it satisfies the same invariant without the per-test
 *   construction duplication (~38 lines across this 19-test class).
 *   If the repository ever grows construction-time behaviour that is
 *   sensitive to *"scheduler installed and currently advancing"*
 *   vs. *"merely installed"*, switch to per-test construction inside
 *   `runTest`. As of this revision the SUT only assigns a cold flow
 *   at construction, so `@Before` is sufficient.
 *
 *   Do **not** demote these to class-level field initializers — those
 *   run before `@Rule.starting()`, which is the actual failure mode
 *   the convention guards against.
 */
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var dataStore: FakeDataStore
    private lateinit var repository: PreferencesRepository

    @Before
    fun setUp() {
        dataStore = FakeDataStore()
        repository = DataStorePreferencesRepository(dataStore)
    }

    // ── Defaults ─────────────────────────────────────────────────

    @Test
    fun `emits defaults when store is empty`() = runTest(mainRule.testDispatcher) {
        repository.settings.test {
            assertEquals(UserPreferences(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Clamping ─────────────────────────────────────────────────

    @Test
    fun `setAmoledThreshold clamps below minimum`() = runTest(mainRule.testDispatcher) {
        repository.setAmoledThreshold(AMOLED_THRESHOLD_MIN - 10)

        repository.settings.test {
            assertEquals(AMOLED_THRESHOLD_MIN, awaitItem().amoledThreshold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAmoledThreshold clamps above maximum`() = runTest(mainRule.testDispatcher) {
        repository.setAmoledThreshold(AMOLED_THRESHOLD_MAX + 999)

        repository.settings.test {
            assertEquals(AMOLED_THRESHOLD_MAX, awaitItem().amoledThreshold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAmoledThreshold preserves in-range values`() = runTest(mainRule.testDispatcher) {
        repository.setAmoledThreshold(17)

        repository.settings.test {
            assertEquals(17, awaitItem().amoledThreshold)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRectWidth clamps non-positive values`() = runTest(mainRule.testDispatcher) {
        repository.setRectWidth(0)

        repository.settings.test {
            assertEquals(RECT_SIZE_MIN, awaitItem().rectWidth)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRectHeight clamps non-positive values`() = runTest(mainRule.testDispatcher) {
        repository.setRectHeight(-5)

        repository.settings.test {
            assertEquals(RECT_SIZE_MIN, awaitItem().rectHeight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCounter clamps values below 1`() = runTest(mainRule.testDispatcher) {
        repository.setCounter(0)

        repository.settings.test {
            assertEquals(SLEEVE_COUNTER_MIN, awaitItem().sleeveCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Counter semantics ────────────────────────────────────────

    @Test
    fun `incrementCounter increments existing value by one`() = runTest(mainRule.testDispatcher) {
        repository.setCounter(5)
        repository.incrementCounter()

        repository.settings.test {
            assertEquals(6, awaitItem().sleeveCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `incrementCounter from default starts from default plus one`() =
        runTest(mainRule.testDispatcher) {
            repository.incrementCounter()

            repository.settings.test {
                assertEquals(SLEEVE_COUNTER_MIN + 1, awaitItem().sleeveCounter)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resetCounter restores minimum`() = runTest(mainRule.testDispatcher) {
        repository.setCounter(42)
        repository.resetCounter()

        repository.settings.test {
            assertEquals(SLEEVE_COUNTER_MIN, awaitItem().sleeveCounter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Filename prefix ──────────────────────────────────────────

    @Test
    fun `setFilenamePrefix falls back to default when blank`() =
        runTest(mainRule.testDispatcher) {
            repository.setFilenamePrefix("   ")

            repository.settings.test {
                assertEquals(DEFAULT_FILENAME_PREFIX, awaitItem().filenamePrefix)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setFilenamePrefix preserves non-blank value`() = runTest(mainRule.testDispatcher) {
        repository.setFilenamePrefix("cover")

        repository.settings.test {
            assertEquals("cover", awaitItem().filenamePrefix)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Booleans and floats (no clamping) ────────────────────────

    @Test
    fun `setAmoledWarmMode persists both truth values`() = runTest(mainRule.testDispatcher) {
        repository.setAmoledWarmMode(true)
        assertEquals(true, currentSnapshot().amoledWarmMode)

        repository.setAmoledWarmMode(false)
        assertEquals(false, currentSnapshot().amoledWarmMode)
    }

    @Test
    fun `setRectX persists float values verbatim`() = runTest(mainRule.testDispatcher) {
        repository.setRectX(123.5f)
        assertEquals(123.5f, currentSnapshot().rectX)
    }

    // ── distinctUntilChanged ─────────────────────────────────────

    @Test
    fun `settings does not re-emit when value is unchanged`() =
        runTest(mainRule.testDispatcher) {
            repository.setAmoledThreshold(10)

            repository.settings.test {
                // Initial snapshot with threshold=10
                assertEquals(10, awaitItem().amoledThreshold)

                // Writing the same value must not produce a new snapshot
                repository.setAmoledThreshold(10)
                expectNoEvents()

                // A genuinely different write does
                repository.setAmoledThreshold(11)
                assertEquals(11, awaitItem().amoledThreshold)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Setters emit to existing subscribers ─────────────────────

    @Test
    fun `multiple setters fan out to a single subscriber`() =
        runTest(mainRule.testDispatcher) {
            repository.settings.test {
                awaitItem() // initial defaults

                repository.setAmoledThreshold(25)
                assertEquals(25, awaitItem().amoledThreshold)

                repository.setAmoledWarmMode(true)
                assertTrue(awaitItem().amoledWarmMode)

                repository.setRectWidth(200)
                assertEquals(200, awaitItem().rectWidth)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── ExportBackground ─────────────────────────────────────────

    @Test
    fun `exportBackground defaults to AMOLED when unset`() = runTest(mainRule.testDispatcher) {
        repository.settings.test {
            assertEquals(ExportBackground.AMOLED, awaitItem().exportBackground)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setExportBackground persists and emits TRANSPARENT`() =
        runTest(mainRule.testDispatcher) {
            repository.setExportBackground(ExportBackground.TRANSPARENT)

            repository.settings.test {
                assertEquals(ExportBackground.TRANSPARENT, awaitItem().exportBackground)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `setExportBackground round-trips AMOLED after TRANSPARENT`() =
        runTest(mainRule.testDispatcher) {
            repository.setExportBackground(ExportBackground.TRANSPARENT)
            repository.setExportBackground(ExportBackground.AMOLED)

            repository.settings.test {
                assertEquals(ExportBackground.AMOLED, awaitItem().exportBackground)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Helpers ──────────────────────────────────────────────────

    private suspend fun currentSnapshot(): UserPreferences {
        var snapshot: UserPreferences? = null
        repository.settings.test {
            snapshot = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return snapshot ?: error("No snapshot received")
    }
}
