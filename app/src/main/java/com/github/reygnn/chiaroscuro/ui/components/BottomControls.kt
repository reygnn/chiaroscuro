package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.reygnn.chiaroscuro.model.EditorState

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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Schwarzes Rechteck ───────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Rechteck", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = state.rectVisible,
                    onClick = onToggleRect,
                    label = { Text(if (state.rectVisible) "Aktiv" else "Inaktiv") }
                )
            }
            if (state.rectVisible) {
                SliderRow("Breite", state.rectWidth,  10..2000, onRectWidthChange)
                SliderRow("Höhe",   state.rectHeight, 10..2000, onRectHeightChange)
                Text("👆 Ziehe das Rechteck auf dem Bild",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // ── AMOLED Analyse ───────────────────────────────────
            Text("AMOLED", style = MaterialTheme.typography.labelMedium)
            SliderRow("Schwelle", state.amoledThreshold, 0..50, onAmoledThreshold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("🌡 Warm-Stich", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.amoledWarmMode, onCheckedChange = { onToggleWarmMode() })
            }
            Text(
                if (state.amoledWarmMode) "Nur warme Dunkeltöne (R-B>3) → Schatten bleiben erhalten"
                else "Empfehlung: 5–15 für Schatten erhalten, 30–50 aggressiv",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.amoledPixelCount > 0) {
                Text("🔴 ${state.amoledPixelCount} Pixel (${String.format("%.1f", state.amoledPercent)}%) fast-schwarz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.amoledAnalysisBitmap == null) {
                    OutlinedButton(
                        onClick = onAnalyzeAmoled,
                        enabled = state.sourceBitmap != null && !state.isAnalyzing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isAnalyzing)
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("🔍 Analysieren")
                    }
                } else {
                    OutlinedButton(onClick = onClearAmoled, modifier = Modifier.weight(1f)) { Text("✕ Zurück") }
                    Button(onClick = onApplyAmoled, modifier = Modifier.weight(1f)) { Text("✅ Korrigieren") }
                }
            }

            HorizontalDivider()

            // ── Laden / Speichern ────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLoadImage, modifier = Modifier.weight(1f)) { Text("📂 Laden") }
                Button(
                    onClick = onSaveTransparent,
                    enabled = state.sourceBitmap != null,
                    modifier = Modifier.weight(1f)
                ) { Text("💾 Speichern") }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Int, valueRange: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text("${value}px", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}