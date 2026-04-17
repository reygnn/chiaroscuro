package com.github.reygnn.chiaroscuro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.reygnn.chiaroscuro.R
import com.github.reygnn.chiaroscuro.ui.components.AppIcons
import com.github.reygnn.chiaroscuro.ui.components.IntSliderRow
import com.github.reygnn.chiaroscuro.ui.components.IntTextFieldRow
import com.github.reygnn.chiaroscuro.ui.components.StringTextFieldRow
import com.github.reygnn.chiaroscuro.viewmodel.PreferencesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    onBack: () -> Unit,
    vm: PreferencesViewModel = viewModel(factory = PreferencesViewModel.Factory),
) {
    val prefs by vm.appPrefs.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_preferences_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(stringResource(R.string.pref_section_fab))
            CheckRow(stringResource(R.string.pref_fab_apply_amoled), prefs.fabApplyAmoled, vm::setFabApplyAmoled)
            CheckRow(stringResource(R.string.pref_fab_place_rect), prefs.fabPlaceRect, vm::setFabPlaceRect)

            HorizontalDivider()

            SectionTitle(stringResource(R.string.pref_section_amoled))
            IntSliderRow(
                label = stringResource(R.string.pref_amoled_threshold),
                value = prefs.amoledThreshold,
                valueRange = 0..50,
                onValueChange = vm::setAmoledThreshold,
                labelStyle = MaterialTheme.typography.bodyMedium,
                labelWidth = 80.dp,
                valueWidth = 32.dp,
            )
            CheckRow(stringResource(R.string.pref_amoled_warm_mode), prefs.amoledWarmMode, vm::setAmoledWarmMode)

            HorizontalDivider()

            val pxSuffix = stringResource(R.string.pref_rect_suffix_px)
            SectionTitle(stringResource(R.string.pref_section_rect))
            IntTextFieldRow(stringResource(R.string.pref_rect_x), prefs.rectX.toInt(), { vm.setRectX(it.toFloat()) }, suffix = pxSuffix)
            IntTextFieldRow(stringResource(R.string.pref_rect_y), prefs.rectY.toInt(), { vm.setRectY(it.toFloat()) }, suffix = pxSuffix)
            IntTextFieldRow(stringResource(R.string.pref_rect_width), prefs.rectWidth, vm::setRectWidth, suffix = pxSuffix)
            IntTextFieldRow(stringResource(R.string.pref_rect_height), prefs.rectHeight, vm::setRectHeight, suffix = pxSuffix)

            HorizontalDivider()

            SectionTitle(stringResource(R.string.pref_section_naming))
            Text(
                text = stringResource(
                    R.string.pref_next_file,
                    prefs.filenamePrefix,
                    prefs.sleeveCounter.toString().padStart(3, '0'),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StringTextFieldRow(stringResource(R.string.pref_prefix), prefs.filenamePrefix, vm::setFilenamePrefix)
            IntTextFieldRow(stringResource(R.string.pref_counter), prefs.sleeveCounter, vm::setCounter)
            OutlinedButton(
                onClick = { vm.resetCounter() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.pref_reset_counter))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}