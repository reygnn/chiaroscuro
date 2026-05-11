package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.reygnn.chiaroscuro.R
import kotlin.math.roundToInt

/**
 * Floating "tools" FAB with a vertical, upward-expanding speed-dial.
 *
 * Layout (bottom-to-top when expanded):
 *   ⚡ Quick Action      — runs the user's preconfigured one-shot pipeline
 *   ▭ Rectangle toggle   — coloured "selected" when [rectVisible] is true
 *   🔍 / ✅              — Analyze AMOLED, swaps to Apply when an overlay is showing
 *   💾 Save              — fires the SAF save launcher
 *   📂 Load              — fires the SAF open-document launcher
 *   ☰ / ✕                — opens the CommandsPanel, swaps to Cancel when an overlay is showing
 *
 * Interaction:
 *  - **Drag** the outer Box to reposition. `change.consume()` prevents the
 *    canvas underneath from receiving the gesture.
 *  - **Tap** the main 🛠 FAB to expand / collapse.
 *  - **Tap** any mini-FAB to run its action and collapse the menu.
 *
 * Position state is local and not persisted across process death.
 */
@Composable
fun EditorFab(
    hasImage: Boolean,
    rectVisible: Boolean,
    showAmoledOverlay: Boolean,
    onQuickAction: () -> Unit,
    onToggleRect: () -> Unit,
    onAnalyzeOrApply: () -> Unit,
    onSave: () -> Unit,
    onLoadImage: () -> Unit,
    onOpenCommandsOrCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offset += drag
                }
            },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = expanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiniFab(
                        symbol = if (showAmoledOverlay) "✕" else "☰",
                        contentDescription = stringResource(
                            if (showAmoledOverlay) R.string.cd_cancel_amoled
                            else R.string.cd_open_commands,
                        ),
                        enabled = true,
                        onClick = { expanded = false; onOpenCommandsOrCancel() },
                    )
                    MiniFab(
                        symbol = "📂",
                        contentDescription = stringResource(R.string.cd_load),
                        enabled = true,
                        onClick = { expanded = false; onLoadImage() },
                    )
                    MiniFab(
                        symbol = "💾",
                        contentDescription = stringResource(R.string.cd_save),
                        enabled = hasImage,
                        onClick = { expanded = false; onSave() },
                    )
                    MiniFab(
                        symbol = if (showAmoledOverlay) "✅" else "🔍",
                        contentDescription = stringResource(
                            if (showAmoledOverlay) R.string.cd_apply_amoled
                            else R.string.cd_analyze,
                        ),
                        enabled = hasImage,
                        onClick = { expanded = false; onAnalyzeOrApply() },
                    )
                    MiniFab(
                        symbol = "▭",
                        contentDescription = stringResource(R.string.cd_rect_toggle),
                        enabled = hasImage,
                        selected = rectVisible,
                        onClick = { expanded = false; onToggleRect() },
                    )
                    MiniFab(
                        symbol = "⚡",
                        contentDescription = stringResource(R.string.cd_quick_action),
                        enabled = hasImage,
                        onClick = { expanded = false; onQuickAction() },
                    )
                }
            }

            val mainFabDesc = stringResource(R.string.cd_editor_fab)
            FloatingActionButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.semantics { contentDescription = mainFabDesc },
            ) {
                Text(if (expanded) "✕" else "🛠", fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun MiniFab(
    symbol: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    SmallFloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = Modifier.semantics {
            this.contentDescription = contentDescription
        },
    ) {
        Text(symbol, fontSize = 20.sp)
    }
}
