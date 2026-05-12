package com.github.reygnn.chiaroscuro.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import app.cash.turbine.test
import com.github.reygnn.chiaroscuro.imaging.BitmapLoader
import com.github.reygnn.chiaroscuro.preferences.UserPreferences
import com.github.reygnn.chiaroscuro.testing.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [EditorViewModel].
 *
 * Scope:
 *   - State transitions driven by user intents (setRect*, toggleRect,
 *     setAmoledThreshold, toggleAmoledWarmMode, updateZoom, resetZoom,
 *     clearAmoledAnalysis, clearExportMessage).
 *   - Repository integration: local state updates eagerly; persistence
 *     follows via viewModelScope.launch; settings-Flow drives init-sync.
 *   - Cross-screen sync: changes made at the repository (e.g. from
 *     PreferencesScreen) reach EditorState without a user intent on
 *     the editor side.
 *   - loadImage state-reset semantics, via the injectable [BitmapLoader]
 *     seam (see the "Load image" section below).
 *
 * Explicitly NOT tested here (JVM unit test scope):
 *   - Bitmap pixel values. android.graphics.Bitmap/Color return defaults
 *     under unitTests.returnDefaultValues=true, making pixel arithmetic
 *     meaningless. Those go into androidTest/ (instrumented).
 *   - applyQuickAction / analyzeAmoled / applyAmoledCorrection and
 *     saveTransparent paths that call into android.graphics.*: they
 *     exist only as integration tests under androidTest/.
 *
 * Ordering convention for Turbine assertions:
 *   - Always awaitItem() for the initial snapshot first.
 *   - advanceUntilIdle() after the action under test to drain launched
 *     coroutines scheduled on the same dispatcher.
 *   - cancelAndIgnoreRemainingEvents() before the block exits.
 */
class EditorViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    // ── Initial sync from repository ─────────────────────────────

    @Test
    fun `init pulls initial values from repository into state`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository(
                UserPreferences(
                    amoledThreshold = 17,
                    amoledWarmMode = true,
                    rectWidth = 123,
                    rectHeight = 234,
                ),
            )
            val vm = EditorViewModel(repository)

            vm.state.test {
                // First snapshot is the default EditorState() before
                // the init-collect has run; advance past the launch.
                awaitItem()
                advanceUntilIdle()

                val synced = awaitItem()
                assertEquals(17, synced.amoledThreshold)
                assertTrue(synced.amoledWarmMode)
                assertEquals(123, synced.rectWidth)
                assertEquals(234, synced.rectHeight)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Cross-screen sync ────────────────────────────────────────

    @Test
    fun `preference changes made externally propagate into editor state`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            // Simulate a change happening from PreferencesScreen / elsewhere
            repository.setAmoledThreshold(33)
            advanceUntilIdle()

            assertEquals(33, vm.state.value.amoledThreshold)
        }

    // ── Rectangle ────────────────────────────────────────────────

    @Test
    fun `setRectWidth updates state eagerly and persists`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.setRectWidth(150)
            // Eagerly visible before coroutine completes
            assertEquals(150, vm.state.value.rectWidth)

            advanceUntilIdle()
            assertEquals(150, repository.current.rectWidth)
        }

    @Test
    fun `setRectHeight updates state eagerly and persists`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.setRectHeight(175)
            assertEquals(175, vm.state.value.rectHeight)

            advanceUntilIdle()
            assertEquals(175, repository.current.rectHeight)
        }

    @Test
    fun `toggleRect flips rectVisible without touching repository`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            assertFalse(vm.state.value.rectVisible)

            vm.toggleRect()
            assertTrue(vm.state.value.rectVisible)

            vm.toggleRect()
            assertFalse(vm.state.value.rectVisible)
        }

    // ── AMOLED setters ───────────────────────────────────────────

    @Test
    fun `setAmoledThreshold updates state, clears overlay, persists`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.setAmoledThreshold(22)
            val s = vm.state.value
            assertEquals(22, s.amoledThreshold)
            assertFalse(s.showAmoledOverlay)
            assertNull(vm.analysisBitmap.value)

            advanceUntilIdle()
            assertEquals(22, repository.current.amoledThreshold)
        }

    @Test
    fun `toggleAmoledWarmMode flips warm mode, clears overlay, persists`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            val initial = vm.state.value.amoledWarmMode
            vm.toggleAmoledWarmMode()
            assertEquals(!initial, vm.state.value.amoledWarmMode)
            assertFalse(vm.state.value.showAmoledOverlay)

            advanceUntilIdle()
            assertEquals(!initial, repository.current.amoledWarmMode)
        }

    @Test
    fun `clearAmoledAnalysis resets overlay and counts`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.clearAmoledAnalysis()

            val s = vm.state.value
            assertFalse(s.showAmoledOverlay)
            assertEquals(0, s.amoledPixelCount)
            assertEquals(0f, s.amoledPercent)
            assertNull(vm.analysisBitmap.value)
        }

    // ── Zoom ─────────────────────────────────────────────────────

    @Test
    fun `updateZoom scales and translates within bounds`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.updateZoom(scaleChange = 2f, offsetChange = Offset(10f, 20f))
            val s1 = vm.state.value
            assertEquals(2f, s1.zoomScale)
            assertEquals(Offset(10f, 20f), s1.zoomOffset)

            vm.updateZoom(scaleChange = 100f, offsetChange = Offset(5f, 5f))
            val s2 = vm.state.value
            // Upper bound is 8f per ZOOM_MAX
            assertEquals(8f, s2.zoomScale)
            assertEquals(Offset(15f, 25f), s2.zoomOffset)
        }

    @Test
    fun `updateZoom clamps scale to minimum`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.updateZoom(scaleChange = 0.01f, offsetChange = Offset.Zero)
            // Lower bound is 0.5f per ZOOM_MIN
            assertEquals(0.5f, vm.state.value.zoomScale)
        }

    @Test
    fun `resetZoom returns scale to one and offset to zero`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.updateZoom(scaleChange = 3f, offsetChange = Offset(50f, 50f))
            assertNotEquals(1f, vm.state.value.zoomScale)

            vm.resetZoom()
            assertEquals(1f, vm.state.value.zoomScale)
            assertEquals(Offset.Zero, vm.state.value.zoomOffset)
        }

    // ── Canvas size ──────────────────────────────────────────────

    @Test
    fun `updateCanvasSize stores the reported size`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            vm.updateCanvasSize(Size(800f, 600f))
            assertEquals(Size(800f, 600f), vm.state.value.canvasSize)
        }

    // ── Load image ───────────────────────────────────────────────
    //
    // loadImage previously replaced _state wholesale with a new
    // EditorState(...), which zeroed canvasSize. Compose's
    // Modifier.onSizeChanged does not re-fire when the layout size is
    // unchanged, so canvasSize then remained Size.Zero for the rest of
    // the session and writeExport's `canvasSize != Size.Zero` guard
    // silently dropped the watermark-cover rectangle from the saved PNG.
    //
    // The two tests below pin the only invariants honestly verifiable on
    // the JVM: canvasSize survives, every other transient field resets.
    // A third test that "clears" analysis state cannot honestly fail on
    // the JVM because analyzeAmoled is unreachable without real Bitmap
    // ops; that contract goes into androidTest/ if it ever becomes worth
    // pinning.

    @Test
    fun `loadImage preserves canvasSize across state reset`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val loader = BitmapLoader { _, _ -> null }
            val vm = EditorViewModel(repository, loader)
            advanceUntilIdle()

            // Simulate Modifier.onSizeChanged having fired at first layout.
            vm.updateCanvasSize(Size(1080f, 2160f))

            vm.loadImage(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            // Regression guard: if canvasSize is reset to Zero here, the
            // export pipeline's `canvasSize != Size.Zero` guard silently
            // drops the watermark-cover rectangle from the saved PNG.
            assertEquals(Size(1080f, 2160f), vm.state.value.canvasSize)
        }

    @Test
    fun `loadImage resets transient editor state but keeps canvas size`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository(
                UserPreferences(rectWidth = 50, rectHeight = 60),
            )
            val loader = BitmapLoader { _, _ -> null }
            val vm = EditorViewModel(repository, loader)
            advanceUntilIdle()

            // Bring state into a "dirty" shape that loading a fresh image
            // should clear.
            vm.updateCanvasSize(Size(800f, 600f))
            vm.toggleRect()
            vm.updateZoom(scaleChange = 2f, offsetChange = Offset(50f, 50f))

            vm.loadImage(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            val s = vm.state.value
            // Transient editor state goes back to defaults…
            assertFalse(s.rectVisible)
            assertEquals(1f, s.zoomScale)
            assertEquals(Offset.Zero, s.zoomOffset)
            // …rect dimensions are re-synced from preferences…
            assertEquals(50, s.rectWidth)
            assertEquals(60, s.rectHeight)
            // …and canvasSize survives.
            assertEquals(Size(800f, 600f), s.canvasSize)
            // isLoading must have flipped back to false once the loader returned.
            assertFalse(s.isLoading)
        }

    // ── Export message ───────────────────────────────────────────

    @Test
    fun `clearExportMessage nulls the message`() =
        runTest(mainRule.testDispatcher) {
            val repository = FakePreferencesRepository()
            val vm = EditorViewModel(repository)
            advanceUntilIdle()

            // We can't trigger exportMessage via saveTransparent in a JVM
            // unit test (needs Context + ContentResolver), but we can
            // assert that clearExportMessage is a no-op-safe operation.
            vm.clearExportMessage()
            assertNull(vm.state.value.exportMessage)
        }
}
