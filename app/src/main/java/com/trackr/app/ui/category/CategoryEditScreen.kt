 package com.trackr.app.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.theme.categoryColorPalette
import com.trackr.app.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.launch

// @spec CAT-UI-017, CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// APP-NAV-004
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(
    onNavigateBack: (errorMessage: String?) -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsState()
    val emoji by viewModel.emoji.collectAsState()
    val color by viewModel.color.collectAsState()
    val valueType by viewModel.valueType.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val valueTypeWarning by viewModel.valueTypeWarning.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    val isEditMode = viewModel.isEditMode

    val scope = rememberCoroutineScope()

    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack("Category not found.")
    }
    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) onNavigateBack(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Category" else "New Category") },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditMode) {
                        IconButton(onClick = { /* delete not yet wired */ }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
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
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = saveResult is SaveResult.ValidationError &&
                        (saveResult as SaveResult.ValidationError).field == "name",
            )

            OutlinedTextField(
                value = emoji,
                onValueChange = { viewModel.emoji.value = it },
                label = { Text("Emoji") },
                modifier = Modifier.fillMaxWidth(),
                isError = saveResult is SaveResult.ValidationError &&
                        (saveResult as SaveResult.ValidationError).field == "emoji",
            )

            Text("Color", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryColorPalette.forEach { paletteColor ->
                    val isSelected = paletteColor == color
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(paletteColor))
                            .then(
                                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.color.value = paletteColor },
                    )
                }
                if (color !in categoryColorPalette) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(3.dp, Color.White, CircleShape),
                    )
                }
            }

            ValueTypeSelector(
                selected = valueType,
                onSelect = { viewModel.valueType.value = it },
            )

            valueTypeWarning?.let { tier ->
                Text(
                    text = when (tier) {
                        ValueTypeWarningTier.IrreversibleSafe ->
                            "Existing events will be converted. This change cannot be reversed by switching back."
                        ValueTypeWarningTier.Partial ->
                            "Some existing events may not be convertible and will display incorrectly."
                        ValueTypeWarningTier.Unsafe ->
                            "Existing events cannot be converted and will display incorrectly."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (valueType == ValueType.Number) {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { viewModel.unit.value = it },
                    label = { Text("Unit (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueTypeSelector(selected: ValueType, onSelect: (ValueType) -> Unit) {
    val types = listOf(
        ValueType.None,
        ValueType.Scale,
        ValueType.Boolean,
        ValueType.Number,
        ValueType.Text,
        ValueType.Duration,
    )
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = valueTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Value type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(valueTypeLabel(type)) },
                    onClick = { onSelect(type); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
