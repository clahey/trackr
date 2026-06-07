 package net.clahey.trackr.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.R
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.EventRow
import net.clahey.trackr.ui.theme.categoryColorPalette
import net.clahey.trackr.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.launch

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013, CAT-UI-017,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-053, CAT-UI-054, CAT-UI-055, CAT-UI-056, CAT-UI-057,
// CAT-UI-059, CAT-UI-060, CAT-UI-061, CAT-NAV-005, CAT-NAV-010, APP-NAV-004
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onNavigateBack: (errorMessage: String?) -> Unit,
    onNavigateToCreateSubCategory: (parentId: String) -> Unit = {},
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsState()
    val emojiUIState by viewModel.emojiUIState.collectAsState()
    val colorState by viewModel.colorState.collectAsState()
    val effectiveColor by viewModel.effectiveColor.collectAsState()
    val effectiveValueType by viewModel.effectiveValueType.collectAsState()
    val numberDefaultUnit by viewModel.numberDefaultUnit.collectAsState()
    val exerciseDefaultSets by viewModel.exerciseDefaultSets.collectAsState()
    val exerciseDefaultReps by viewModel.exerciseDefaultReps.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val valueTypeWarning by viewModel.valueTypeWarning.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    val isColorInherited by viewModel.isColorInherited.collectAsState()
    val isValueTypeInherited by viewModel.isValueTypeInherited.collectAsState()
    val parentCategory by viewModel.parentCategory.collectAsState()
    val previewCategory by viewModel.previewCategory.collectAsState()
    val previewEvent by viewModel.previewEvent.collectAsState()
    val isEditMode = viewModel.isEditMode

    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()
    val scope = rememberCoroutineScope()

    val categoryNotFound = stringResource(R.string.category_not_found)
    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack(categoryNotFound)
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
                title = { Text(stringResource(if (isEditMode) R.string.category_edit_title else R.string.category_new_title)) },
                navigationIcon = {
                    IconButton(onClick = { onNavigateBack(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // @spec CAT-UI-012, CAT-UI-013, CAT-UI-004, CAT-UI-005, CAT-NAV-005
                    if (isEditMode) {
                        IconButton(onClick = { viewModel.requestDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    // @spec CAT-UI-053, CAT-NAV-010
                    if (isEditMode && parentCategory == null) {
                        IconButton(onClick = {
                            viewModel.categoryId?.let { onNavigateToCreateSubCategory(it) }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_create_subcategory))
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
            EventRow(event = previewEvent, category = previewCategory, hasMismatch = false, onClick = null)

            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.name.value = it },
                label = { Text(stringResource(R.string.category_field_name)) },
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

            Text(stringResource(R.string.category_field_color), style = MaterialTheme.typography.labelMedium)
            // @spec CAT-UI-056
            ColorPicker(
                colorState = colorState,
                parentCategory = parentCategory,
                effectiveColor = effectiveColor,
                isColorInherited = isColorInherited,
                onSelectColor = { viewModel.colorState.value = it },
            )

            // @spec CAT-UI-057
            ValueTypeSelector(
                selected = effectiveValueType,
                isValueTypeInherited = isValueTypeInherited,
                parentCategory = parentCategory,
                onSelect = { viewModel.valueTypeState.value = it },
                onSelectInherit = { viewModel.valueTypeState.value = null },
            )

            valueTypeWarning?.let { tier ->
                Text(
                    text = stringResource(when (tier) {
                        ValueTypeWarningTier.IrreversibleSafe -> R.string.category_warning_irreversible_safe
                        ValueTypeWarningTier.Partial -> R.string.category_warning_partial
                        ValueTypeWarningTier.Unsafe -> R.string.category_warning_unsafe
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // @spec CAT-UI-011
            if (effectiveValueType == ValueType.Number) {
                OutlinedTextField(
                    value = numberDefaultUnit,
                    onValueChange = { viewModel.updateNumberDefaultUnit(it) },
                    label = { Text(stringResource(R.string.category_field_unit_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // @spec CAT-UI-011a
            if (effectiveValueType == ValueType.Exercise) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = exerciseDefaultSets,
                        onValueChange = { viewModel.updateExerciseDefaultSets(it) },
                        label = { Text(stringResource(R.string.category_field_default_sets)) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = exerciseDefaultReps,
                        onValueChange = { viewModel.updateExerciseDefaultReps(it) },
                        label = { Text(stringResource(R.string.category_field_default_reps)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
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
                Text(stringResource(R.string.category_emoji_inherit))
            }
        }
        OutlinedTextField(
            value = if (isInherited) parentEmoji ?: "" else emojiUIState.customValue,
            onValueChange = { onUIStateChange(emojiUIState.copy(mode = EmojiMode.CUSTOM, customValue = it)) },
            label = { Text(stringResource(R.string.category_field_emoji)) },
            modifier = Modifier.weight(1f),
            enabled = !isInherited,
            isError = isError,
        )
    }
}

private data class SwatchSpec(
    val color: Long,
    val cells: Int = 1,
    val label: String? = null,
    val isSelected: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

@Composable
private fun Swatch(spec: SwatchSpec, width: Dp, height: Dp, shape: Shape) {
    val modifier = Modifier
        .width(width)
        .height(height)
        .clip(shape)
        .background(Color(spec.color))
        .then(if (spec.isSelected) Modifier.border(3.dp, Color.White, shape) else Modifier)
        .then(spec.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        if (spec.label != null) {
            Text(
                text = spec.label,
                color = Color(foregroundColorForBackground(spec.color)),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ColorPicker(
    colorState: Long?,
    parentCategory: Category.MetaCategory?,
    effectiveColor: Long,
    isColorInherited: Boolean,
    onSelectColor: (Long?) -> Unit,
) {
    val swatchHeight = 44.dp
    val cornerShape = RoundedCornerShape(8.dp)
    val spacing = 6.dp

    val inheritedLabel = stringResource(R.string.category_color_inherited)
    val customLabel = stringResource(R.string.category_color_custom)
    BoxWithConstraints {
        val columns = if (maxWidth >= 480.dp) 12 else 6
        val swatchWidth = (maxWidth - spacing * (columns - 1)) / columns

        val hasCustomColor = colorState != null && colorState !in categoryColorPalette
        val rows: List<List<SwatchSpec>> = buildList {
            buildList {
                if (parentCategory != null) add(SwatchSpec(
                    color = parentCategory.color, cells = 3, label = inheritedLabel,
                    isSelected = isColorInherited, onClick = { onSelectColor(null) },
                ))
                if (hasCustomColor) add(SwatchSpec(
                    color = effectiveColor, cells = 3, label = customLabel,
                    isSelected = true,
                ))
            }.takeIf { it.isNotEmpty() }?.let { add(it) }
            categoryColorPalette.chunked(columns).forEach { chunk ->
                add(chunk.map { color -> SwatchSpec(
                    color = color, isSelected = color == colorState,
                    onClick = { onSelectColor(color) },
                )})
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    row.forEach { spec ->
                        Swatch(
                            spec = spec,
                            width = swatchWidth * spec.cells + spacing * (spec.cells - 1),
                            height = swatchHeight,
                            shape = cornerShape,
                        )
                    }
                }
            }
        }
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
    val inheritLabel = parentCategory?.let {
        stringResource(R.string.category_value_type_inherit, it.name, stringResource(valueTypeStringRes(it.valueType)))
    }

    val displayLabel = if (inheritLabel != null && isValueTypeInherited) inheritLabel
    else stringResource(valueTypeStringRes(selected))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category_field_value_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (inheritLabel != null) {
                DropdownMenuItem(
                    text = { Text(inheritLabel) },
                    onClick = { onSelectInherit(); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
            types.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(valueTypeStringRes(type))) },
                    onClick = { onSelect(type); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
