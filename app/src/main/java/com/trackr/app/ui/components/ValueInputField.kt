package com.trackr.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import kotlin.time.Duration.Companion.seconds

// @spec EL-UI-058, EL-UI-059, EL-UI-060
@Composable
fun ValueInputField(
    value: EventValue?,
    onValueChange: (EventValue?) -> Unit,
    valueType: ValueType? = null,
    autoFocus: Boolean = false,
) {
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
            val num = (value as? EventValue.NumberValue)
            val focusRequester = remember { FocusRequester() }
            if (autoFocus) LaunchedEffect(Unit) { focusRequester.requestFocus() }
            OutlinedTextField(
                value = num?.value?.toString() ?: "",
                onValueChange = { s ->
                    val d = s.toDoubleOrNull()
                    if (d != null) onValueChange(EventValue.NumberValue(d, num?.unit))
                    else if (s.isEmpty()) onValueChange(null)
                },
                label = { Text("Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("value_input_field"),
            )
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
