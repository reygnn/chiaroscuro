package com.github.reygnn.chiaroscuro.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.viewmodel.PreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    vm: PreferencesViewModel = viewModel()
) {
    val prefs by vm.appPrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── FAB Steps ────────────────────────────────────────
            SectionTitle("FAB Quick Action")
            CheckRow("Apply AMOLED correction", prefs.fabApplyAmoled) { vm.setFabApplyAmoled(it) }
            CheckRow("Place black rectangle",   prefs.fabPlaceRect)   { vm.setFabPlaceRect(it) }

            HorizontalDivider()

            // ── AMOLED ───────────────────────────────────────────
            SectionTitle("AMOLED Filter")
            SliderRow("Threshold", prefs.amoledThreshold, 0..50) { vm.setAmoledThreshold(it) }
            CheckRow("Warm Tint Mode (R-B > 3 only)", prefs.amoledWarmMode) { vm.setAmoledWarmMode(it) }

            HorizontalDivider()

            // ── Rectangle ────────────────────────────────────────
            SectionTitle("Black Rectangle")
            NumberRow("X",      prefs.rectX.toInt())      { vm.setRectX(it.toFloat()) }
            NumberRow("Y",      prefs.rectY.toInt())      { vm.setRectY(it.toFloat()) }
            NumberRow("Width",  prefs.rectWidth)          { vm.setRectWidth(it) }
            NumberRow("Height", prefs.rectHeight)         { vm.setRectHeight(it) }

            HorizontalDivider()

            // ── Sleeve Counter ───────────────────────────────────
            SectionTitle("Sleeve Counter")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Next file: sleeve_${prefs.sleeveCounter.toString().padStart(3, '0')}.png",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { vm.resetCounter() }) {
                    Text("Reset to 001")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.weight(1f)
        )
        Text("$value", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onValueChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.let { onValueChange(it) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
            suffix = { Text("px", style = MaterialTheme.typography.labelSmall) }
        )
    }
}