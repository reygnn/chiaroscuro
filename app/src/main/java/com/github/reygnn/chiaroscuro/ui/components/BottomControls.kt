package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.reygnn.chiaroscuro.R
import com.github.reygnn.chiaroscuro.model.EditorState
import java.util.Locale

@Composable
fun BottomControls(
    state: EditorState,
    onRectWidthChange: (Int) -> Unit,
    onRectHeightChange: (Int) -> Unit,
    onToggleRect: () -> Unit,
    onLoadImage: () -> Unit,
    onSaveTransparent: () -> Unit,
    onAmoledThreshold: (Int) -> Unit,
    onToggleWarmMode: () -> Unit,
    onAnalyzeAmoled: () -> Unit,
    onApplyAmoled: () -> Unit,
    onClearAmoled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.bc_rectangle),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.rectVisible,
                    onClick = onToggleRect,
                    label = {
                        Text(
                            if (state.rectVisible) stringResource(R.string.bc_rect_active)
                            else stringResource(R.string.bc_rect_inactive),
                        )
                    },
                )
            }
            if (state.rectVisible) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!state.showAmoledOverlay) {
                    OutlinedButton(
                        onClick = onAnalyzeAmoled,
                        enabled = !state.isAnalyzing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(stringResource(R.string.bc_amoled_analyze))
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onClearAmoled,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.bc_amoled_cancel)) }
                    Button(
                        onClick = onApplyAmoled,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.bc_amoled_apply)) }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onLoadImage,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bc_load)) }
                Button(
                    onClick = onSaveTransparent,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.bc_save)) }
            }
        }
    }
}