package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.reygnn.chiaroscuro.R
import com.github.reygnn.chiaroscuro.model.EditorState
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Floating Material card hosting the less-frequent editor controls:
 *  - Load image / Preferences entry
 *  - Rectangle width + height sliders (only shown while the rectangle is active)
 *  - AMOLED threshold slider, warm-tint switch, hints, near-black stats
 *
 * Apply / Cancel for the AMOLED analysis overlay live in the speed-dial
 * mini-FABs (see EditorFab), not in this panel — see TECHNICAL.md / the
 * fab-ui refactor notes for the rationale.
 *
 * Drag handle is the title row only. The Card body cannot be draggable
 * because it hosts Sliders, whose internal pointer handling would
 * compete with the drag gesture.
 */
@Composable
fun CommandsPanel(
    state: EditorState,
    onLoadImage: () -> Unit,
    onOpenPreferences: () -> Unit,
    onRectWidthChange: (Int) -> Unit,
    onRectHeightChange: (Int) -> Unit,
    onAmoledThreshold: (Int) -> Unit,
    onToggleWarmMode: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .widthIn(min = 280.dp, max = 360.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offset += drag
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.cp_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                val closeDesc = stringResource(R.string.cd_close_panel)
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.semantics { contentDescription = closeDesc },
                ) {
                    Text("✕")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onLoadImage,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bc_load)) }
                OutlinedButton(
                    onClick = onOpenPreferences,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.menu_preferences)) }
            }

            if (state.rectVisible) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.bc_rectangle),
                    style = MaterialTheme.typography.labelMedium,
                )
                val pxSuffix = stringResource(R.string.pref_rect_suffix_px)
                IntSliderRow(
                    label = stringResource(R.string.bc_rect_width),
                    value = state.rectWidth,
                    valueRange = 10..2000,
                    onValueChange = onRectWidthChange,
                    valueSuffix = pxSuffix,
                )
                IntSliderRow(
                    label = stringResource(R.string.bc_rect_height),
                    value = state.rectHeight,
                    valueRange = 10..2000,
                    onValueChange = onRectHeightChange,
                    valueSuffix = pxSuffix,
                )
                Text(
                    text = stringResource(R.string.bc_rect_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.bc_amoled),
                style = MaterialTheme.typography.labelMedium,
            )
            IntSliderRow(
                label = stringResource(R.string.bc_amoled_threshold),
                value = state.amoledThreshold,
                valueRange = 0..50,
                onValueChange = onAmoledThreshold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.bc_amoled_warm_tint),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.amoledWarmMode,
                    onCheckedChange = { onToggleWarmMode() },
                )
            }
            Text(
                text = if (state.amoledWarmMode)
                    stringResource(R.string.bc_amoled_warm_hint)
                else
                    stringResource(R.string.bc_amoled_default_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.amoledPixelCount > 0) {
                Text(
                    text = stringResource(
                        R.string.bc_amoled_near_black_stats,
                        state.amoledPixelCount,
                        String.format(Locale.US, "%.1f", state.amoledPercent),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
