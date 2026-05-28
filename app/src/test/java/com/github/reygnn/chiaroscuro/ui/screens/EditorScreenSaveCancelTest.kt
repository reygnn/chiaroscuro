package com.github.reygnn.chiaroscuro.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.github.reygnn.chiaroscuro.imaging.BitmapLoader
import com.github.reygnn.chiaroscuro.preferences.UserPreferences
import com.github.reygnn.chiaroscuro.testing.MainDispatcherRule
import com.github.reygnn.chiaroscuro.viewmodel.EditorViewModel
import com.github.reygnn.chiaroscuro.viewmodel.FakePreferencesRepository
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for Patch 04 (save-cancel leaves stuck proposedFilename).
 *
 * The companion unit test in `EditorViewModelTest` pins the VM-side contract
 * (`clearProposedFilename` nulls the field). This test pins the *binding*:
 * that the `saveLauncher` cancel branch in [EditorScreen] actually calls
 * the VM endpoint when the SAF picker is dismissed without a URI. Removing
 * the `else` branch from the launcher callback would silently re-introduce
 * the bug — and would NOT break the VM-side test — but it would break this
 * one.
 *
 * Approach:
 *   - `createComposeRule()` hosts the Composable without needing a real
 *     Activity (avoids a TestActivity class + manifest registration).
 *   - `LocalActivityResultRegistryOwner` is overridden via
 *     [CompositionLocalProvider] with a registry that immediately dispatches
 *     [Activity.RESULT_CANCELED] (the same outcome as a user back-press or
 *     swipe-dismiss on the SAF picker). `rememberLauncherForActivityResult`
 *     reads its registry from this CompositionLocal, so no source change
 *     to `EditorScreen` is required for test injectability.
 *   - The VM uses the project's [MainDispatcherRule] and is driven via
 *     [advanceUntilIdle] in the standard pattern; [createComposeRule]'s
 *     `waitForIdle` then drains recomposition + LaunchedEffect.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorScreenSaveCancelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `save dialog cancel clears the proposed filename`() =
        runTest(mainRule.testDispatcher) {
            // fabApplyAmoled = false so applyQuickAction skips the
            // ImageProcessing.applyAmoledCorrection call that would otherwise
            // require real Bitmap pixel access — same trick the VM unit test
            // for clearProposedFilename uses.
            val repository = FakePreferencesRepository(
                UserPreferences(
                    fabApplyAmoled = false,
                    fabPlaceRect = false,
                ),
            )
            val src = mockk<Bitmap>(relaxed = true)
            val loader = BitmapLoader { _, _ -> src }
            val vm = EditorViewModel(repository, loader)
            advanceUntilIdle()

            // Pre-load so applyQuickAction won't early-return on null source.
            vm.loadImage(mockk(relaxed = true), mockk(relaxed = true))
            advanceUntilIdle()

            // Registry that synthesises a user-cancel for any launch:
            // dispatchResult(requestCode, RESULT_CANCELED, null) is exactly
            // the path the framework takes when the SAF Documents UI is
            // dismissed without selecting a destination. The registry hands
            // the null result back through the launcher's onResult callback,
            // which is the EditorScreen lambda under test.
            val cancelRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    dispatchResult(requestCode, Activity.RESULT_CANCELED, null as Intent?)
                }
            }
            val registryOwner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = cancelRegistry
            }

            composeTestRule.setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides registryOwner,
                ) {
                    EditorScreen(
                        onOpenPreferences = {},
                        viewModel = vm,
                    )
                }
            }
            composeTestRule.waitForIdle()

            // applyQuickAction sets proposedFilename → recomposition →
            // LaunchedEffect(state.proposedFilename) fires →
            // saveLauncher.launch(filename) → cancelRegistry.onLaunch →
            // dispatchResult(RESULT_CANCELED) → saveLauncher callback
            // receives uri = null → EditorScreen's else-branch calls
            // viewModel.clearProposedFilename() → state.proposedFilename = null.
            vm.applyQuickAction()
            advanceUntilIdle()

            // Baseline: applyQuickAction did populate the field. Without this
            // check, a regression where applyQuickAction silently no-op'd
            // would also pass the final assertNull.
            //
            // Note: this is observed BEFORE waitForIdle drains the
            // LaunchedEffect that triggers the cancel cycle.
            assertNotNull(
                "applyQuickAction must set proposedFilename to drive the SAF picker",
                vm.state.value.proposedFilename,
            )

            composeTestRule.waitForIdle()
            advanceUntilIdle()

            // The actual regression guard. Without the cancel-branch wiring
            // in EditorScreen.saveLauncher, this stays non-null and the FAB
            // would appear dead on the next Quick-Action with an unchanged
            // counter.
            assertNull(
                "Save-cancel must release proposedFilename via clearProposedFilename()",
                vm.state.value.proposedFilename,
            )
        }
}
