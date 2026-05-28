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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.theme.categoryColorPalette
import com.trackr.app.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.launch

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013, CAT-UI-017,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-053, CAT-UI-054, CAT-UI-055, CAT-UI-056, CAT-UI-057,
// CAT-UI-059, CAT-UI-060, CAT-UI-061, CAT-NAV-005, CAT-NAV-010, APP-NAV-004
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditScreen(
    onNavigateBack: (errorMessage: String?) -> Unit,
    onNavigateToCreateSubCategory: (parentId: String) -> Unit = {},
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsState()
    val emojiUIState by viewModel.emojiUIState.collectAsState()
    val colorState by viewModel.colorState.collectAsState()
    val effectiveEmoji by viewModel.effectiveEmoji.collectAsState()
    val effectiveColor by viewModel.effectiveColor.collectAsState()
    val effectiveValueType by viewModel.effectiveValueType.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val valueTypeWarning by viewModel.valueTypeWarning.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    val isColorInherited by viewModel.isColorInherited.collectAsState()
    val isValueTypeInherited by viewModel.isValueTypeInherited.collectAsState()
    val parentCategory by viewModel.parentCategory.collectAsState()
    val isEditMode = viewModel.isEditMode

    val isSubCategoryMode = parentCategory != null
    val isMetaCategoryEditMode = isEditMode && !isSubCategoryMode

    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack("Category not found.")
    }
    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) onNavigateBack(null)
    }

    // @spec CAT-UI-004, CAT-UI-005, CAT-NAV-005
    pendingDelete?.let { confirmation ->
        DeleteCategoryDialog(
            confirmation = confirmation,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
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
                    // @spec CAT-UI-012, CAT-UI-013, CAT-UI-004, CAT-UI-005, CAT-NAV-005
                    if (isEditMode) {
                        IconButton(onClick = { viewModel.requestDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    // @spec CAT-UI-053, CAT-NAV-010
                    if (isMetaCategoryEditMode) {
                        IconButton(onClick = {
                            viewModel.editingCategoryId?.let { onNavigateToCreateSubCategory(it) }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Create subcategory")
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
            // @spec CAT-UI-059, CAT-UI-060
            PreviewCard(
                emoji = effectiveEmoji,
                color = effectiveColor,
                name = name,
                valueType = effectiveValueType,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = saveResult is SaveResult.ValidationError &&
                        (saveResult as SaveResult.ValidationError).field == "name",
            )

            EmojiField(
                emojiUIState = emojiUIState,
                parentEmoji = parentCategory?.emoji,
                isError = saveResult is SaveResult.ValidationError &&
                        (saveResult as SaveResult.ValidationError).field == "emoji",
                onUIStateChange = { viewModel.emojiUIState.value = it },
            )

            Text("Color", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // @spec CAT-UI-056
                if (isSubCategoryMode) {
                    val parentColor = parentCategory!!.color
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(parentColor))
                            .then(
                                if (isColorInherited) Modifier.border(3.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.colorState.value = null },
                    ) {
                        Text(
                            text = "↑",
                            color = Color(foregroundColorForBackground(parentColor)),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                categoryColorPalette.forEach { paletteColor ->
                    val isSelected = paletteColor == colorState
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(paletteColor))
                            .then(
                                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.colorState.value = paletteColor },
                    )
                }
                if (colorState != null && colorState !in categoryColorPalette) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(effectiveColor))
                            .border(3.dp, Color.White, CircleShape),
                    )
                }
            }

            // @spec CAT-UI-057
            ValueTypeSelector(
                selected = effectiveValueType,
                isValueTypeInherited = isValueTypeInherited,
                parentCategory = if (isSubCategoryMode) parentCategory else null,
                onSelect = { viewModel.valueTypeState.value = it },
                onSelectInherit = { viewModel.valueTypeState.value = null },
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

            if (effectiveValueType == ValueType.Number) {
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

@Composable
private fun PreviewCard(
    emoji: String,
    color: Long,
    name: String,
    valueType: ValueType,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(color)),
            ) {
                Text(
                    text = emoji.ifEmpty { "?" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name.ifEmpty { "Category name" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = valueTypeLabel(valueType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// @spec CAT-UI-055, CAT-UI-061
@Composable
private fun EmojiField(
    emojiUIState: EmojiUIState,
    parentEmoji: String?,
    isError: Boolean,
    onUIStateChange: (EmojiUIState) -> Unit,
) {
    val isInherited = parentEmoji != null && emojiUIState.mode == EmojiMode.INHERIT
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (parentEmoji != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Checkbox(
                    checked = isInherited,
                    onCheckedChange = { checked ->
                        onUIStateChange(emojiUIState.copy(
                            mode = if (checked) EmojiMode.INHERIT else EmojiMode.CUSTOM,
                        ))
                    },
                )
                Text("Inherit")
            }
        }
        OutlinedTextField(
            value = if (isInherited) parentEmoji ?: "" else emojiUIState.customValue,
            onValueChange = { onUIStateChange(emojiUIState.copy(mode = EmojiMode.CUSTOM, customValue = it)) },
            label = { Text("Emoji") },
            modifier = Modifier.weight(1f),
            enabled = !isInherited,
            isError = isError,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueTypeSelector(
    selected: ValueType,
    isValueTypeInherited: Boolean,
    parentCategory: Category.MetaCategory?,
    onSelect: (ValueType) -> Unit,
    onSelectInherit: () -> Unit,
) {
    val types = listOf(
        ValueType.None,
        ValueType.Scale,
        ValueType.Boolean,
        ValueType.Number,
        ValueType.Text,
        ValueType.Duration,
        ValueType.Exercise,
    )
    var expanded by remember { mutableStateOf(false) }

    val displayLabel = if (parentCategory != null && isValueTypeInherited) {
        "Same as ${parentCategory.name} (${valueTypeLabel(parentCategory.valueType)})"
    } else {
        valueTypeLabel(selected)
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Value type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (parentCategory != null) {
                DropdownMenuItem(
                    text = { Text("Same as ${parentCategory.name} (${valueTypeLabel(parentCategory.valueType)})") },
                    onClick = { onSelectInherit(); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
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
