package com.github.reygnn.chiaroscuro.testing

/**
 * Testing conventions for this project. Read this before writing a test.
 *
 * ────────────────────────────────────────────────────────────────────
 * 1. Dispatcher discipline
 * ────────────────────────────────────────────────────────────────────
 *
 * Every test that touches coroutines uses exactly one [TestDispatcher],
 * installed via [MainDispatcherRule] as @get:Rule. The rule's
 * testDispatcher is also passed to runTest:
 *
 *     @get:Rule val mainRule = MainDispatcherRule()
 *
 *     @Test fun example() = runTest(mainRule.testDispatcher) { ... }
 *
 * Do NOT create separate TestScope or StandardTestDispatcher instances
 * inside a test. Doing so makes Dispatchers.Main and the test body
 * drive different schedulers, which silently breaks advanceUntilIdle().
 *
 * ────────────────────────────────────────────────────────────────────
 * 2. ViewModel construction — always inside runTest
 * ────────────────────────────────────────────────────────────────────
 *
 * Do NOT promote ViewModels (or their collaborators like repositories)
 * to test-class fields:
 *
 *     // ❌ WRONG — binds viewModelScope to the REAL Dispatchers.Main,
 *     //    which never runs under a JVM test.
 *     private val repository = FakePreferencesRepository()
 *     private val vm = PreferencesViewModel(repository)
 *
 * [MainDispatcherRule] installs its test dispatcher as Dispatchers.Main
 * in JUnit's @Rule setup phase, which runs AFTER field initializers.
 * A VM built as a field captures the real Main in its viewModelScope;
 * every launch/stateIn/etc. then dispatches into a scheduler the test
 * cannot drain. Symptom: setters appear to do nothing, `advanceUntilIdle`
 * has no effect, assertions see initial state.
 *
 * Always construct VMs and their collaborators inside the runTest block:
 *
 *     @Test fun example() = runTest(mainRule.testDispatcher) {
 *         val repository = FakePreferencesRepository()
 *         val vm = PreferencesViewModel(repository)
 *         // …
 *     }
 *
 * Cost: a few extra lines per test. Benefit: the VM's viewModelScope
 * uses the same dispatcher as the test body, and advanceUntilIdle()
 * actually drains the work the VM scheduled.
 *
 * ────────────────────────────────────────────────────────────────────
 * 3. Mocking
 * ────────────────────────────────────────────────────────────────────
 *
 * MockK, relaxed = true for collaborators we only assert against on
 * specific calls. For Flow-returning methods, stub with MutableStateFlow
 * or flowOf so the subject-under-test sees deterministic emissions.
 *
 * For interfaces that back both production code and tests — like
 * [com.github.reygnn.chiaroscuro.preferences.PreferencesRepository] —
 * prefer a hand-rolled in-memory fake over a mock. Fakes encode the
 * contract once and are reused across many tests; mocks of Flow-heavy
 * interfaces tend to produce brittle ordering assertions and stale
 * emissions.
 *
 * ────────────────────────────────────────────────────────────────────
 * 4. Flow assertions
 * ────────────────────────────────────────────────────────────────────
 *
 * Turbine for StateFlow / Flow assertions:
 *
 *     subject.state.test {
 *         assertEquals(Expected, awaitItem())
 *         cancelAndIgnoreRemainingEvents()
 *     }
 *
 * Avoid collecting into a local mutable list unless Turbine's API
 * genuinely does not fit the scenario — Turbine's awaitItem() makes
 * the ordering constraints explicit.
 *
 * ────────────────────────────────────────────────────────────────────
 * 5. Android framework classes
 * ────────────────────────────────────────────────────────────────────
 *
 * Unit tests run on the JVM with unitTests.isReturnDefaultValues = true.
 * Calls into android.graphics.Bitmap, Canvas, Color, etc. return
 * default values and will make image-processing tests meaningless.
 *
 * Pixel-level tests therefore do NOT go here — they belong in
 * androidTest/ (instrumented). Unit tests on this layer cover the
 * ViewModel's orchestration (did it call the repository? did it
 * flip isAnalyzing? did it update state in the right order?), not
 * the bitmap pixel values themselves.
 *
 * ────────────────────────────────────────────────────────────────────
 * 6. JUnit assertions, not kotlin.test
 * ────────────────────────────────────────────────────────────────────
 *
 * Use org.junit.Assert.* for assertions. kotlin.test.* is the KMP
 * assertion API and requires the kotlin-test dependency, which this
 * project does not ship.
 */
private object TestingConventions