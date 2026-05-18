package com.trackr.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

// @spec EL-UI-040, EL-UI-042, EL-UI-043, EL-UI-044, EL-NAV-005, EL-NAV-006, EL-PROC-002, APP-NAV-003
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val timestamp by viewModel.timestamp.collectAsState()
    val value by viewModel.value.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val imagePaths by viewModel.imagePaths.collectAsState()
    val isValueEditable by viewModel.isValueEditable.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val deleteComplete by viewModel.deleteComplete.collectAsState()

    val scope = rememberCoroutineScope()

    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) onNavigateBack()
    }
    LaunchedEffect(deleteComplete) {
        if (deleteComplete) onNavigateBack()
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete event?") },
            text = { Text("This event will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.confirmDelete() } }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Event") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancel()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestDelete() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = timestampFormatter.format(timestamp),
                onValueChange = {},
                readOnly = true,
                label = { Text("Timestamp") },
                modifier = Modifier.fillMaxWidth(),
            )

            if (isValueEditable) {
                ValueInputField(
                    value = value,
                    onValueChange = { viewModel.value.value = it },
                )
            } else {
                Text("Value: ${value?.let { formatValue(it) } ?: "—"} (read-only)")
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (imagePaths.isNotEmpty()) {
                Text("Images (${imagePaths.size})")
                imagePaths.forEach { path ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(path, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeImage(path) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove image")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun ValueInputField(
    value: EventValue?,
    onValueChange: (EventValue?) -> Unit,
    valueType: ValueType? = null,
) {
    when {
        value is EventValue.Scale || valueType == ValueType.Scale -> {
            val scale = (value as? EventValue.Scale)?.value ?: 5
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
            val bool = (value as? EventValue.BooleanValue)?.value
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
            OutlinedTextField(
                value = num?.value?.toString() ?: "",
                onValueChange = { s ->
                    val d = s.toDoubleOrNull()
                    if (d != null) onValueChange(EventValue.NumberValue(d, num?.unit))
                    else if (s.isEmpty()) onValueChange(null)
                },
                label = { Text("Value") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        value is EventValue.TextValue || valueType == ValueType.Text -> {
            OutlinedTextField(
                value = (value as? EventValue.TextValue)?.text ?: "",
                onValueChange = { onValueChange(EventValue.TextValue(it)) },
                label = { Text("Value") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
        value is EventValue.DurationValue || valueType == ValueType.Duration -> {
            val dur = (value as? EventValue.DurationValue)?.duration ?: kotlin.time.Duration.ZERO
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
                        modifier = Modifier.weight(1f),
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
    is EventValue.ErrorValue -> "[Error: ${value.raw}]"
}
