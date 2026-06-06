package com.trackr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackr.app.R
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.EventValue

// @spec DM-PROC-011, EL-UI-050, EL-UI-051, EL-UI-051b, EL-UI-052, EL-UI-052c, EL-UI-053,
// EL-UI-055, EL-UI-055c, EL-UI-055d, EL-UI-056, EL-UI-058, EL-UI-059, EL-UI-059b,
// EL-UI-062, EL-UI-063, EL-UI-064, EL-UI-065, EL-UI-066
@Composable
fun ValueInputField(
    uiState: ValueUIState,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    when (uiState) {
        ValueUIState.None -> {}
        is ValueUIState.Scale -> ScaleInput(uiState, onStateChange)
        is ValueUIState.Bool -> BoolInput(uiState, onStateChange)
        is ValueUIState.Number -> NumberInput(uiState, onStateChange, autoFocus, onDone)
        is ValueUIState.Text -> TextInput(uiState, onStateChange, autoFocus)
        is ValueUIState.Duration -> DurationInput(uiState, onStateChange, autoFocus, onDone)
        is ValueUIState.Exercise -> ExerciseInput(uiState, onStateChange, autoFocus, onDone)
        is ValueUIState.ReadOnly -> ReadOnlyDisplay(uiState)
        is ValueUIState.Mismatched -> MismatchedInput(uiState, onStateChange, autoFocus)
    }
}

@Composable
private fun ScaleInput(uiState: ValueUIState.Scale, onStateChange: (ValueUIState) -> Unit) {
    Column {
        Text(stringResource(R.string.value_input_scale, uiState.value))
        Slider(
            value = uiState.value.toFloat(),
            onValueChange = { onStateChange(uiState.copy(value = it.toInt())) },
            valueRange = 1f..10f,
            steps = 8,
        )
    }
}

// @spec EL-UI-051, EL-UI-051b
@Composable
private fun BoolInput(uiState: ValueUIState.Bool, onStateChange: (ValueUIState) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (uiState.selected == true) {
            Button(
                onClick = { onStateChange(uiState.copy(selected = true)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.value_input_yes)) }
        } else {
            OutlinedButton(
                onClick = { onStateChange(uiState.copy(selected = true)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.value_input_yes)) }
        }
        if (uiState.selected == false) {
            Button(
                onClick = { onStateChange(uiState.copy(selected = false)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.value_input_no)) }
        } else {
            OutlinedButton(
                onClick = { onStateChange(uiState.copy(selected = false)) },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.value_input_no)) }
        }
    }
}

// @spec EL-UI-052, EL-UI-052c, EL-UI-058
@Composable
private fun NumberInput(
    uiState: ValueUIState.Number,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean,
    onDone: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val doneOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default)
    val doneActions = KeyboardActions(onDone = { onDone?.invoke() })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.text,
            onValueChange = { onStateChange(uiState.copy(text = it)) },
            label = { Text(stringResource(R.string.value_input_value)) },
            keyboardOptions = doneOptions.copy(keyboardType = KeyboardType.Decimal),
            keyboardActions = doneActions,
            modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("value_input_field"),
        )
        OutlinedTextField(
            value = uiState.unit,
            onValueChange = { onStateChange(uiState.copy(unit = it)) },
            label = { Text(stringResource(R.string.value_input_unit)) },
            keyboardOptions = doneOptions,
            keyboardActions = doneActions,
            modifier = Modifier.weight(1f),
        )
    }
}

// @spec EL-UI-053, EL-UI-058
@Composable
private fun TextInput(
    uiState: ValueUIState.Text,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = uiState.text,
        onValueChange = { onStateChange(uiState.copy(text = it)) },
        label = { Text(stringResource(R.string.value_input_value)) },
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("value_input_field"),
        minLines = 2,
    )
}

// @spec EL-UI-055, EL-UI-055c, EL-UI-055d, EL-UI-058
@Composable
private fun DurationInput(
    uiState: ValueUIState.Duration,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean,
    onDone: (() -> Unit)? = null,
) {
    val hoursFocus = remember { FocusRequester() }
    val minutesFocus = remember { FocusRequester() }
    val secondsFocus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { hoursFocus.requestFocus() }
    val numberNext = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
    val doneActions = KeyboardActions(onDone = { onDone?.invoke() })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.hoursText,
            onValueChange = { onStateChange(uiState.copy(hoursText = it)) },
            label = { Text(stringResource(R.string.value_input_hours)) },
            keyboardOptions = numberNext,
            keyboardActions = KeyboardActions(onNext = { minutesFocus.requestFocus() }),
            modifier = Modifier.weight(1f).focusRequester(hoursFocus).testTag("value_duration_h"),
        )
        OutlinedTextField(
            value = uiState.minutesText,
            onValueChange = { onStateChange(uiState.copy(minutesText = it)) },
            label = { Text(stringResource(R.string.value_input_minutes)) },
            keyboardOptions = numberNext,
            keyboardActions = KeyboardActions(onNext = { secondsFocus.requestFocus() }),
            modifier = Modifier.weight(1f).focusRequester(minutesFocus),
        )
        OutlinedTextField(
            value = uiState.secondsText,
            onValueChange = { onStateChange(uiState.copy(secondsText = it)) },
            label = { Text(stringResource(R.string.value_input_seconds)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
            keyboardActions = doneActions,
            modifier = Modifier.weight(1f).focusRequester(secondsFocus),
        )
    }
}

// @spec EL-UI-059, EL-UI-059b
@Composable
private fun ExerciseInput(
    uiState: ValueUIState.Exercise,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    val setsFocus = remember { FocusRequester() }
    val repsFocus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { setsFocus.requestFocus() }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.setsText,
            onValueChange = { onStateChange(uiState.copy(setsText = it)) },
            label = { Text(stringResource(R.string.value_input_sets)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { repsFocus.requestFocus() }),
            modifier = Modifier.weight(1f).focusRequester(setsFocus),
        )
        OutlinedTextField(
            value = uiState.repsText,
            onValueChange = { onStateChange(uiState.copy(repsText = it)) },
            label = { Text(stringResource(R.string.value_input_reps)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.weight(1f).focusRequester(repsFocus),
        )
    }
}

// @spec EL-UI-056
@Composable
private fun ReadOnlyDisplay(uiState: ValueUIState.ReadOnly) {
    Text(
        text = uiState.displayText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// @spec EL-UI-062, EL-UI-063, EL-UI-064, EL-UI-065, EL-UI-066
@Composable
private fun MismatchedInput(
    uiState: ValueUIState.Mismatched,
    onStateChange: (ValueUIState) -> Unit,
    autoFocus: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.mismatch_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            val buttonLabel = when (val outcome = uiState.outcome) {
                is ConversionOutcome.Converted -> stringResource(R.string.mismatch_convert, describeValue(outcome.value))
                is ConversionOutcome.UsedDefault -> stringResource(R.string.mismatch_replace_default, describeValue(outcome.value))
                ConversionOutcome.Discard -> stringResource(R.string.mismatch_discard)
            }
            TextButton(onClick = {
                val newState = when (val outcome = uiState.outcome) {
                    is ConversionOutcome.Converted -> outcome.value.toValueUIState()
                    is ConversionOutcome.UsedDefault -> outcome.value.toValueUIState()
                    ConversionOutcome.Discard -> ValueUIState.None
                }
                onStateChange(newState)
            }) { Text(buttonLabel) }
        }
    }

    val editableState = uiState.editableState
    if (editableState != null) {
        ValueInputField(
            uiState = editableState,
            onStateChange = { newSub -> onStateChange(uiState.copy(editableState = newSub)) },
            autoFocus = autoFocus,
        )
    }
}

fun formatValue(value: EventValue): String = when (value) {
    is EventValue.Scale -> "${value.value}/10"
    is EventValue.BooleanValue -> if (value.value) "Yes" else "No"
    is EventValue.NumberValue -> buildString {
        append(value.value)
        value.unit?.let { append(" $it") }
    }
    is EventValue.TextValue -> value.text
    is EventValue.DurationValue -> value.duration.toString()
    is EventValue.ExerciseValue -> "${value.sets} × ${value.reps}"
    is EventValue.ErrorValue -> "[Error: ${value.raw}]"
}

// @spec EL-UI-063, EL-UI-064
fun describeValue(value: EventValue): String = when (value) {
    is EventValue.Scale -> "${value.value}/10"
    is EventValue.BooleanValue -> if (value.value) "Yes" else "No"
    is EventValue.NumberValue -> buildString {
        append(value.value)
        value.unit?.let { append(" $it") }
    }
    is EventValue.TextValue -> value.text
    is EventValue.DurationValue -> value.duration.toString()
    is EventValue.ExerciseValue -> "${value.sets} sets × ${value.reps} reps"
    is EventValue.ErrorValue -> value.raw
}
