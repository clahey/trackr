package net.clahey.trackr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.clahey.trackr.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFieldFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val timeFieldFormatter = DateTimeFormatter.ofPattern("h:mm a")

// M3 DatePickerState represents the selection as UTC midnight of the selected date, not a
// local-zone instant -- converting via the system default zone can shift the date by one day
// near timezone boundaries, so this conversion is pinned to UTC specifically.
fun utcMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

fun localDateToUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun combineDateAndTime(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Instant =
    LocalTime.of(hour, minute).atDate(date).atZone(zone).toInstant()

// @spec EL-UI-031, EL-UI-032, EL-UI-040, EL-UI-043
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampField(timestamp: Instant, onTimestampChange: (Instant) -> Unit, enabled: Boolean = true) {
    val zone = remember { ZoneId.systemDefault() }
    val localDateTime = remember(timestamp, zone) { timestamp.atZone(zone) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = dateFieldFormatter.format(localDateTime),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(stringResource(R.string.event_field_date)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDatePicker) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Click-catcher on top of the whole field -- OutlinedTextField's own pointer-input
            // handling for the text/content area otherwise swallows clicks before a clickable()
            // on the field's own modifier ever sees them, leaving only the label reliably tappable.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { showDatePicker = true },
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = timeFieldFormatter.format(localDateTime),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text(stringResource(R.string.event_field_time)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTimePicker) },
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = enabled) { showTimePicker = true },
            )
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(localDateTime.toLocalDate()),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    showDatePicker = false
                    if (millis != null) {
                        val date = utcMillisToLocalDate(millis)
                        onTimestampChange(combineDateAndTime(date, localDateTime.hour, localDateTime.minute, zone))
                    }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = LocalTime.of(localDateTime.hour, localDateTime.minute),
            onConfirm = {
                val date = localDateTime.toLocalDate()
                onTimestampChange(combineDateAndTime(date, it.hour, it.minute, zone))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}
