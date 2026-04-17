package com.github.reygnn.chiaroscuro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * OutlinedTextField for integer input that keeps user keystrokes in local
 * state and only commits to the caller on focus loss. External value
 * changes (e.g. from a DataStore Flow) are only applied to the visible
 * text when the field is not currently focused, so mid-typing is never
 * overwritten by a late emission.
 */
@Composable
fun IntTextFieldRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 80.dp,
    suffix: String? = null,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) text = value.toString()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(labelWidth),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    if (wasFocused && !focusState.isFocused) {
                        text.toIntOrNull()?.let(onValueChange)
                    }
                },
            suffix = suffix?.let { s ->
                { Text(s, style = MaterialTheme.typography.labelSmall) }
            },
        )
    }
}

/**
 * Same commit-on-blur pattern as [IntTextFieldRow], for String input.
 */
@Composable
fun StringTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 80.dp,
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) text = value
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(labelWidth),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    val wasFocused = isFocused
                    isFocused = focusState.isFocused
                    if (wasFocused && !focusState.isFocused) {
                        onValueChange(text)
                    }
                },
        )
    }
}

/**
 * Slider whose integer value is committed to the caller only on
 * onValueChangeFinished (finger up). Intermediate drag frames update
 * local state and the visible number but do not fan out, avoiding
 * storms of DataStore writes during a gesture.
 *
 * remember(value) is intentionally keyed on the external value: a
 * slider, unlike a text field, has no concurrent user input that could
 * be lost by re-syncing, and should visibly snap when another source
 * changes the value.
 */
@Composable
fun IntSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle? = null,
    labelWidth: Dp = 72.dp,
    valueSuffix: String = "",
    valueWidth: Dp = 52.dp,
) {
    var dragValue by remember(value) { mutableFloatStateOf(value.toFloat()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = labelStyle ?: MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(labelWidth),
        )
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${dragValue.toInt()}$valueSuffix",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(valueWidth),
            textAlign = TextAlign.End,
        )
    }
}