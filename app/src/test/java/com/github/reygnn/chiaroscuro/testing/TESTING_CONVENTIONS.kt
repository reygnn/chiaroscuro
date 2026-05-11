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
 * JVM unit tests run with unitTests.isReturnDefaultValues = true. Calls
 * into android.graphics.Bitmap, Canvas, Color, etc. return default
 * values (null, 0, false) — image-processing assertions based on those
 * return values are meaningless.
 *
 * Pixel-level logic therefore lives in pure Kotlin kernels
 * (AmoledTransform, ImageGeometry) that are trivially JVM-testable
 * without any Android imports. The thin ImageProcessing adapter does
 * the Bitmap↔IntArray conversion and is NOT directly unit-tested —
 * see its top-of-class comment for the testing policy.
 *
 * ────────────────────────────────────────────────────────────────────
 * 6. JUnit assertions, not kotlin.test
 * ────────────────────────────────────────────────────────────────────
 *
 * Use org.junit.Assert.* for assertions. kotlin.test.* is the KMP
 * assertion API and requires the kotlin-test dependency, which this
 * project does not ship.
 *
 * ────────────────────────────────────────────────────────────────────
 * 7. Robolectric — targeted exception, not the default
 * ────────────────────────────────────────────────────────────────────
 *
 * This project does NOT use Robolectric for general testing. Pure JVM
 * tests are faster and cover all of our logic. Robolectric is added
 * only to exercise framework behavior that cannot be reproduced on
 * the JVM — where the default-values stub gives a misleading green.
 *
 * Currently, ONE such test exists: ImageProcessingRobolectricTest.
 * It guards a Bitmap.hasAlpha + compress(PNG) interaction that caused
 * a real transparency regression. The test pins the behavior so the
 * same bug cannot return silently.
 *
 * If you find yourself reaching for Robolectric, first ask:
 *
 *   1. Can the logic be extracted into a pure-Kotlin kernel?
 *      (Almost always yes — see AmoledTransform / ImageGeometry.)
 *   2. Is this testing *framework behavior*, not our own code?
 *      (Only then is Robolectric justified.)
 *   3. Is the risk of silent regression high enough to pay the cost
 *      of a Robolectric sandbox (~30 s cold start, ~1 s warm)?
 *
 * Robolectric tests use @RunWith(AndroidJUnit4::class) + @Config(sdk = …)
 * and live in src/test/. Current toolchain pins Robolectric 4.15.1 /
 * SDK 35 so tests run on JDK 17. Upgrading to Robolectric 4.16+ for
 * SDK 36 parity would require JDK 21 as the test runtime.
 */
private object TestingConventions