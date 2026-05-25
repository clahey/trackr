package com.trackr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.domain.convertOrDefault
import com.trackr.app.domain.matchesValueType
import kotlin.time.Duration.Companion.seconds

// @spec DM-PROC-011, EL-UI-052, EL-UI-058, EL-UI-059, EL-UI-060, EL-UI-062, EL-UI-063, EL-UI-064, EL-UI-065, EL-UI-066
@Composable
fun ValueInputField(
    value: EventValue?,
    onValueChange: (EventValue?) -> Unit,
    valueType: ValueType? = null,
    autoFocus: Boolean = false,
    defaultUnit: String? = null,
) {
    val hasMismatch = valueType != null && value != null && !matchesValueType(value, valueType)
    val outcome = if (hasMismatch) convertOrDefault(value!!, valueType!!) else null

    // @spec EL-UI-062, EL-UI-063, EL-UI-064, EL-UI-065, EL-UI-066
    if (outcome != null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Stored value doesn't match the category type.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                val buttonLabel = when (outcome) {
                    is ConversionOutcome.Converted -> "Convert to ${describeValue(outcome.value)}"
                    is ConversionOutcome.UsedDefault -> "Replace with default: ${describeValue(outcome.value)}"
                    ConversionOutcome.Discard -> "Discard value"
                }
                TextButton(onClick = {
                    when (outcome) {
                        is ConversionOutcome.Converted -> onValueChange(outcome.value)
                        is ConversionOutcome.UsedDefault -> onValueChange(outcome.value)
                        ConversionOutcome.Discard -> onValueChange(null)
                    }
                }) { Text(buttonLabel) }
            }
        }
    }

    val showInput = outcome !is ConversionOutcome.Discard && value !is EventValue.ErrorValue
    if (!showInput) return

    when {
        value is EventValue.Scale || valueType == ValueType.Scale -> {
            val scale = (value as? EventValue.Scale ?: EventValue.Scale()).value
            Column {
                Text("Scale: $scale")
                Slider(
                    value = scale.toFloat(),
                    onValueChange = { onValueChange(EventValue.Scale(it.toInt())) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }
        }
        value is EventValue.BooleanValue || valueType == ValueType.Boolean -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onValueChange(EventValue.BooleanValue(true)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Yes") }
                Button(
                    onClick = { onValueChange(EventValue.BooleanValue(false)) },
                    modifier = Modifier.weight(1f),
                ) { Text("No") }
            }
        }
        value is EventValue.NumberValue || valueType == ValueType.Number -> {
            val num = value as? EventValue.NumberValue
            // @spec DM-PROC-011: unit seeded from category at first keystroke via defaultUnit
            var unitText by remember(defaultUnit) { mutableStateOf(num?.unit ?: defaultUnit ?: "") }
            val focusRequester = remember { FocusRequester() }
            if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = num?.value?.toString() ?: "",
                    onValueChange = { s ->
                        val d = s.toDoubleOrNull()
                        if (d != null) onValueChange(EventValue.NumberValue(d, unitText.takeIf { it.isNotBlank() }))
                        else if (s.isEmpty()) onValueChange(null)
                    },
                    label = { Text("Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("value_input_field"),
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { u ->
                        unitText = u
                        num?.let { n -> onValueChange(EventValue.NumberValue(n.value, u.takeIf { it.isNotBlank() })) }
                    },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        value is EventValue.TextValue || valueType == ValueType.Text -> {
            val focusRequester = remember { FocusRequester() }
            if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = (value as? EventValue.TextValue)?.text ?: "",
                onValueChange = { onValueChange(EventValue.TextValue(it)) },
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("value_input_field"),
                minLines = 2,
            )
        }
        value is EventValue.ExerciseValue || valueType == ValueType.Exercise -> {
            val ex = value as? EventValue.ExerciseValue ?: EventValue.ExerciseValue()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ex.sets.toString(),
                    onValueChange = { s ->
                        val sets = s.toIntOrNull() ?: return@OutlinedTextField
                        onValueChange(EventValue.ExerciseValue(sets, ex.reps))
                    },
                    label = { Text("Sets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = ex.reps.toString(),
                    onValueChange = { r ->
                        val reps = r.toIntOrNull() ?: return@OutlinedTextField
                        onValueChange(EventValue.ExerciseValue(ex.sets, reps))
                    },
                    label = { Text("Reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        value is EventValue.DurationValue || valueType == ValueType.Duration -> {
            val dur = (value as? EventValue.DurationValue ?: EventValue.DurationValue()).duration
            val hoursFocusRequester = remember { FocusRequester() }
            if (autoFocus) LaunchedEffect(Unit) { hoursFocusRequester.requestFocus() }
            dur.toComponents { hours, minutes, seconds, _ ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hours.toString(),
                        onValueChange = { h ->
                            val hrs = h.toLongOrNull() ?: 0L
                            onValueChange(EventValue.DurationValue((hrs * 3600 + minutes * 60 + seconds).seconds))
                        },
                        label = { Text("H") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).focusRequester(hoursFocusRequester).testTag("value_duration_h"),
                    )
                    OutlinedTextField(
                        value = minutes.toString(),
                        onValueChange = { m ->
                            val mins = m.toIntOrNull() ?: 0
                            onValueChange(EventValue.DurationValue((hours * 3600 + mins * 60 + seconds).seconds))
                        },
                        label = { Text("M") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = seconds.toString(),
                        onValueChange = { s ->
                            val secs = s.toIntOrNull() ?: 0
                            onValueChange(EventValue.DurationValue((hours * 3600 + minutes * 60 + secs).seconds))
                        },
                        label = { Text("S") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        else -> {}
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
