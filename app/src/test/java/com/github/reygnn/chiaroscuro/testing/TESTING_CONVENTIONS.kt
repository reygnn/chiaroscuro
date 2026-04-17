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
 * 2. Mocking
 * ────────────────────────────────────────────────────────────────────
 *
 * MockK, relaxed = true for collaborators we only assert against on
 * specific calls. For Flow-returning methods, stub with MutableStateFlow
 * or flowOf so the subject-under-test sees deterministic emissions.
 *
 * ────────────────────────────────────────────────────────────────────
 * 3. Flow assertions
 * ────────────────────────────────────────────────────────────────────
 *
 * Turbine for StateFlow / Flow assertions:
 *
 *     subject.state.test {
 *         assertEquals(Expected, awaitItem())
 *     }
 *
 * Avoid collecting into a local mutable list unless Turbine's API
 * genuinely does not fit the scenario — Turbine's awaitItem() makes
 * the ordering constraints explicit.
 *
 * ────────────────────────────────────────────────────────────────────
 * 4. Android framework classes
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
 */
private object TestingConventions