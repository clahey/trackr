package net.clahey.trackr.ui.category

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.R
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.ValueTypeWarningTier
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.EventRow
import net.clahey.trackr.ui.components.OutlinedFieldBox
import net.clahey.trackr.ui.components.ReminderPermissionNotice
import net.clahey.trackr.ui.components.dialogMessageRes
import net.clahey.trackr.ui.components.dialogTitleRes
import net.clahey.trackr.ui.components.rememberReminderPermissionProblem
import net.clahey.trackr.ui.components.UnsavedChangesDialog
import net.clahey.trackr.ui.theme.categoryColorPalette
import net.clahey.trackr.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013, CAT-UI-017,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-053, CAT-UI-054, CAT-UI-055, CAT-UI-056, CAT-UI-057,
// CAT-UI-059, CAT-UI-060, CAT-UI-061, CAT-UI-067, CAT-UI-068, CAT-UI-069, CAT-UI-070,
// CAT-UI-071, CAT-UI-072, CAT-UI-073, CAT-UI-074, CAT-UI-075, CAT-UI-076,
// CAT-NAV-005, CAT-NAV-006, CAT-NAV-010, APP-NAV-004
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onNavigateBack: (errorMessage: String?) -> Unit,
    onNavigateToCreateSubCategory: (parentId: String) -> Unit = {},
    onCategoryCreated: (categoryId: String) -> Unit = {},
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val name by viewModel.name.collectAsState()
    val emojiUIState by viewModel.emojiUIState.collectAsState()
    val colorState by viewModel.colorState.collectAsState()
    val effectiveColor by viewModel.effectiveColor.collectAsState()
    val effectiveValueType by viewModel.effectiveValueType.collectAsState()
    val valueTypeState by viewModel.valueTypeState.collectAsState()
    val numberDefaultUnit by viewModel.numberDefaultUnit.collectAsState()
    val exerciseDefaultSets by viewModel.exerciseDefaultSets.collectAsState()
    val exerciseDefaultReps by viewModel.exerciseDefaultReps.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val valueTypeWarning by viewModel.valueTypeWarning.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    val savedCategoryId by viewModel.savedCategoryId.collectAsState()
    val isColorInherited = colorState == null
    val isValueTypeInherited = valueTypeState == null
    val parentCategory by viewModel.parentCategory.collectAsState()
    val previewCategory by viewModel.previewCategory.collectAsState()
    val previewEvent by viewModel.previewEvent.collectAsState()
    val isEditMode = viewModel.isEditMode
    val isDirty by viewModel.isDirty.collectAsState()
    val hasUserEdits by viewModel.hasUserEdits.collectAsState()
    // @spec CAT-UI-018
    val isLoaded by viewModel.isLoaded.collectAsState()

    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()
    val scope = rememberCoroutineScope()
    var showBackDiscardDialog by remember { mutableStateOf(false) }

    // @spec REM-UI-001..011, REM-PERM-001..004
    val reminderUIState by viewModel.reminderUIState.collectAsState()
    val pendingPermissionConfirmation by viewModel.pendingPermissionConfirmation.collectAsState()

    val context = LocalContext.current
    fun notificationsGranted() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun exactAlarmAvailable() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun doSave(force: Boolean = false) {
        scope.launch {
            viewModel.save(
                notificationPermissionGranted = notificationsGranted(),
                exactAlarmAvailable = exactAlarmAvailable(),
                forceSaveDespitePermission = force,
            )
        }
    }

    // @spec CAT-NAV-006 — only warn once the user has actually edited a field
    BackHandler(enabled = hasUserEdits) { showBackDiscardDialog = true }

    val categoryNotFound = stringResource(R.string.category_not_found)
    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack(categoryNotFound)
    }
    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) {
            // @spec CAT-NAV-020 — report the id of a genuinely new category before popping
            if (!isEditMode) savedCategoryId?.let { onCategoryCreated(it) }
            onNavigateBack(null)
        }
    }

    // @spec REM-PERM-003
    pendingPermissionConfirmation?.let { problem ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionConfirmation() },
            title = { Text(stringResource(problem.dialogTitleRes())) },
            text = { Text(stringResource(problem.dialogMessageRes())) },
            confirmButton = {
                TextButton(onClick = { doSave(force = true) }) {
                    Text(stringResource(R.string.reminder_permission_save_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionConfirmation() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // @spec CAT-UI-004, CAT-UI-005, CAT-NAV-005
    pendingDelete?.let { confirmation ->
        DeleteCategoryDialog(
            confirmation = confirmation,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    // @spec CAT-NAV-006
    if (showBackDiscardDialog) {
        UnsavedChangesDialog(
            onSave = { doSave() },
            onDiscard = { onNavigateBack(null) },
            onCancel = { showBackDiscardDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditMode) R.string.category_edit_title else R.string.category_new_title)) },
                navigationIcon = {
                    // @spec CAT-NAV-006
                    IconButton(onClick = {
                        if (hasUserEdits) showBackDiscardDialog = true
                        else onNavigateBack(null)
                    }) {
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
                onValueChange = { viewModel.setName(it) },
                label = { Text(stringResource(R.string.category_field_name)) },
                modifier = Modifier.fillMaxWidth(),
                // @spec CAT-UI-018
                enabled = isLoaded,
                isError = (saveResult as? SaveResult.ValidationError)?.field == "name",
            )

            val emojiIsError = (saveResult as? SaveResult.ValidationError)?.field == "emoji"
            // @spec CAT-UI-074, CAT-UI-076
            OutlinedFieldBox(
                label = stringResource(R.string.category_field_emoji),
                isError = emojiIsError,
                enabled = isLoaded,
            ) {
                EmojiField(
                    emojiUIState = emojiUIState,
                    parentEmoji = parentCategory?.emoji,
                    isError = emojiIsError,
                    enabled = isLoaded,
                    onUIStateChange = { viewModel.setEmojiUIState(it) },
                )
            }

            // @spec CAT-UI-056, CAT-UI-075
            OutlinedFieldBox(label = stringResource(R.string.category_field_color), enabled = isLoaded) {
                ColorPicker(
                    colorState = colorState,
                    parentCategory = parentCategory,
                    effectiveColor = effectiveColor,
                    isColorInherited = isColorInherited,
                    enabled = isLoaded,
                    onSelectColor = { viewModel.setColorState(it) },
                )
            }

            // @spec CAT-UI-057
            ValueTypeSelector(
                selected = effectiveValueType,
                isValueTypeInherited = isValueTypeInherited,
                parentCategory = parentCategory,
                enabled = isLoaded,
                onSelect = { viewModel.setValueTypeState(it) },
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
                    // @spec CAT-UI-018
                    enabled = isLoaded,
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
                        // @spec CAT-UI-018
                        enabled = isLoaded,
                    )
                    OutlinedTextField(
                        value = exerciseDefaultReps,
                        onValueChange = { viewModel.updateExerciseDefaultReps(it) },
                        label = { Text(stringResource(R.string.category_field_default_reps)) },
                        modifier = Modifier.weight(1f),
                        // @spec CAT-UI-018
                        enabled = isLoaded,
                    )
                }
            }

            // @spec REM-UI-001..011, REM-PERM-001, REM-PERM-002
            ReminderSection(
                enabled = isLoaded,
                reminderOn = reminderUIState.enabled,
                mode = reminderUIState.mode,
                times = reminderUIState.times,
                windowStart = reminderUIState.windowStart,
                windowEnd = reminderUIState.windowEnd,
                occurrencesPerDay = reminderUIState.occurrencesPerDay,
                daysActive = reminderUIState.daysActive,
                showCategoryInNotification = reminderUIState.showCategoryInNotification,
                validationField = (saveResult as? SaveResult.ValidationError)?.field,
                onReminderOnChange = { viewModel.setReminderEnabled(it) },
                onModeChange = { viewModel.setReminderMode(it) },
                onTimesChange = { viewModel.setReminderTimes(it) },
                onWindowStartChange = { viewModel.setReminderWindowStart(it) },
                onWindowEndChange = { viewModel.setReminderWindowEnd(it) },
                onOccurrencesPerDayChange = { viewModel.setReminderOccurrencesPerDay(it) },
                onDaysActiveChange = { viewModel.setReminderDaysActive(it) },
                onShowCategoryInNotificationChange = { viewModel.setReminderShowCategoryInNotification(it) },
            )

            // @spec CAT-UI-067, CAT-UI-018
            if (isDirty) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { doSave() },
                    enabled = isLoaded,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

private val QUICK_PICK_EMOJIS = listOf(
    "🏃", "🏋️", "🚴", "🧘", "💪", "🤸",
    "💊", "🩺", "🌡️", "🩹",
    "😴", "💧", "☕", "🍎", "🥗",
    "😊", "😢", "🧠", "❤️", "🌿",
    "🌞", "📚", "🎵", "🎮", "🎯",
)

// @spec CAT-UI-055, CAT-UI-061, CAT-UI-068, CAT-UI-069, CAT-UI-070, CAT-UI-071, CAT-UI-072, CAT-UI-073
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiField(
    emojiUIState: EmojiUIState,
    parentEmoji: String?,
    isError: Boolean,
    // @spec CAT-UI-018
    enabled: Boolean,
    onUIStateChange: (EmojiUIState) -> Unit,
) {
    val isInherited = parentEmoji != null && emojiUIState.mode == EmojiMode.INHERIT
    val currentCustomEmoji = emojiUIState.customValue

    var showPicker by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val selectedIndex = remember(currentCustomEmoji, isInherited) {
        if (!isInherited) QUICK_PICK_EMOJIS.indexOf(currentCustomEmoji).takeIf { it >= 0 }
        else null
    }
    LaunchedEffect(selectedIndex) {
        selectedIndex?.let { listState.animateScrollToItem(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // @spec CAT-UI-055
        if (parentEmoji != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = isInherited,
                        enabled = enabled,
                        onValueChange = { checked ->
                            onUIStateChange(emojiUIState.copy(
                                mode = if (checked) EmojiMode.INHERIT else EmojiMode.CUSTOM,
                            ))
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.category_emoji_inherit),
                    modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                )
                Text(
                    text = parentEmoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(
                        if (!enabled) DISABLED_ALPHA else if (isInherited) 1f else DISABLED_ALPHA
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = isInherited, onCheckedChange = null, enabled = enabled)
            }
        }

        // @spec CAT-UI-068, CAT-UI-069
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(QUICK_PICK_EMOJIS) { emoji ->
                val isSelected = !isInherited && emoji == currentCustomEmoji
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            else Modifier
                        )
                        .alpha(if (enabled) 1f else DISABLED_ALPHA)
                        .clickable(enabled = enabled) {
                            onUIStateChange(EmojiUIState(EmojiMode.CUSTOM, emoji))
                        },
                ) {
                    Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        // @spec CAT-UI-070, CAT-UI-073
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { showPicker = true }, enabled = enabled) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.category_emoji_browse))
            }
            if (!isInherited && currentCustomEmoji.isNotEmpty() && currentCustomEmoji !in QUICK_PICK_EMOJIS) {
                Text(text = currentCustomEmoji, style = MaterialTheme.typography.titleLarge)
            }
        }

        if (isError) {
            Text(
                text = stringResource(R.string.category_emoji_error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    // @spec CAT-UI-070, CAT-UI-071
    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            AndroidView(
                factory = { context -> EmojiPickerView(context) },
                update = { view ->
                    view.setOnEmojiPickedListener { item ->
                        onUIStateChange(EmojiUIState(EmojiMode.CUSTOM, item.emoji))
                        showPicker = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            )
        }
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
private fun Swatch(spec: SwatchSpec, width: Dp, height: Dp, shape: Shape, enabled: Boolean) {
    val modifier = Modifier
        .width(width)
        .height(height)
        .clip(shape)
        // @spec CAT-UI-018 — a swatch is a bare clickable Box, so it needs the disabled dimming
        // applied by hand; Material would have supplied it.
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
        .background(Color(spec.color))
        .then(if (spec.isSelected) Modifier.border(3.dp, Color.White, shape) else Modifier)
        .then(spec.onClick?.let { Modifier.clickable(enabled = enabled, onClick = it) } ?: Modifier)
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

// @spec CAT-UI-014, CAT-UI-015, CAT-UI-016
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun ColorPicker(
    colorState: Long?,
    parentCategory: Category.MetaCategory?,
    effectiveColor: Long,
    isColorInherited: Boolean,
    // @spec CAT-UI-018
    enabled: Boolean,
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
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// @spec CAT-UI-047
@Composable
private fun ValueTypeSelector(
    selected: ValueType,
    isValueTypeInherited: Boolean,
    parentCategory: Category.MetaCategory?,
    // @spec CAT-UI-018
    enabled: Boolean,
    onSelect: (ValueType?) -> Unit,
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

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            // @spec CAT-UI-018
            enabled = enabled,
            label = { Text(stringResource(R.string.category_field_value_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (inheritLabel != null) {
                DropdownMenuItem(
                    text = { Text(inheritLabel) },
                    onClick = { onSelect(null); expanded = false },
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

// Material's disabled alpha token. Applied by hand to the form's raw `clickable` surfaces, which —
// unlike Material components — render identically whether or not they still accept clicks, so
// without this they'd sit at full saturation next to dimmed text fields and switches.
// @spec CAT-UI-018
private const val DISABLED_ALPHA = 0.38f

private fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a"))

// @spec REM-UI-001, REM-UI-002, REM-UI-003, REM-UI-007, REM-UI-008, REM-UI-009, REM-UI-010,
// REM-UI-011, REM-PERM-001, REM-PERM-002
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    // @spec CAT-UI-018 — the screen's load gate. `reminderOn` is the reminder's own on/off state
    // (REM-UI-002/003); `enabled` keeps Compose's meaning of "this control accepts input".
    enabled: Boolean,
    reminderOn: Boolean,
    mode: ReminderMode,
    times: List<LocalTime>,
    windowStart: LocalTime,
    windowEnd: LocalTime,
    occurrencesPerDay: Int,
    daysActive: Set<DayOfWeek>,
    showCategoryInNotification: Boolean,
    validationField: String?,
    onReminderOnChange: (Boolean) -> Unit,
    onModeChange: (ReminderMode) -> Unit,
    onTimesChange: (List<LocalTime>) -> Unit,
    onWindowStartChange: (LocalTime) -> Unit,
    onWindowEndChange: (LocalTime) -> Unit,
    onOccurrencesPerDayChange: (Int) -> Unit,
    onDaysActiveChange: (Set<DayOfWeek>) -> Unit,
    onShowCategoryInNotificationChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // The grant itself is live-checked elsewhere; this only tracks whether the dialog is up, so the
    // inline prompt can keep quiet while it is.
    var notificationRequestInFlight by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationRequestInFlight = false }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notificationRequestInFlight = true
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // @spec REM-UI-002
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text(stringResource(R.string.reminder_section_title))
            }
            Switch(
                checked = reminderOn,
                enabled = enabled,
                onCheckedChange = onReminderOnChange,
            )
        }

        // @spec REM-UI-003
        if (reminderOn) {
            // Fires once each time this content is entered — turning the section on, or
            // reopening an already-on one — which is both of the triggers REM-PERM-001 names, so
            // the Switch itself doesn't request as well.
            // @spec REM-PERM-001
            LaunchedEffect(Unit) { requestNotificationPermissionIfNeeded() }

            // Read unconditionally so the permission-state receiver isn't torn down and
            // re-registered every time the runtime dialog opens and closes.
            // @spec REM-PERM-002
            val permissionProblem = rememberReminderPermissionProblem()
            if (!notificationRequestInFlight) {
                permissionProblem?.let { problem ->
                    ReminderPermissionNotice(problem, shape = MaterialTheme.shapes.medium)
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ReminderMode.FIXED,
                    enabled = enabled,
                    onClick = { onModeChange(ReminderMode.FIXED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.reminder_mode_fixed)) }
                SegmentedButton(
                    selected = mode == ReminderMode.RANDOM,
                    enabled = enabled,
                    onClick = { onModeChange(ReminderMode.RANDOM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.reminder_mode_random)) }
            }

            if (mode == ReminderMode.FIXED) {
                // @spec REM-UI-004
                ReminderTimesEditor(times = times, enabled = enabled, onTimesChange = onTimesChange)
                if (validationField == "reminder_times") {
                    Text(
                        stringResource(R.string.reminder_validation_no_time),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                // @spec REM-UI-005
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TimePickerFieldButton(
                        label = stringResource(R.string.reminder_window_start),
                        time = windowStart,
                        enabled = enabled,
                        onTimeChange = onWindowStartChange,
                        modifier = Modifier.weight(1f),
                    )
                    TimePickerFieldButton(
                        label = stringResource(R.string.reminder_window_end),
                        time = windowEnd,
                        enabled = enabled,
                        onTimeChange = onWindowEndChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                // @spec REM-UI-006
                OutlinedTextField(
                    value = occurrencesPerDay.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { if (it in 1..12) onOccurrencesPerDayChange(it) } },
                    label = { Text(stringResource(R.string.reminder_occurrences_per_day)) },
                    modifier = Modifier.fillMaxWidth(),
                    // @spec CAT-UI-018
                    enabled = enabled,
                )
                if (validationField == "reminder_window") {
                    Text(
                        stringResource(R.string.reminder_validation_window_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            // @spec REM-UI-007
            Text(
                stringResource(R.string.reminder_active_days),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            )
            DaysOfWeekRow(daysActive = daysActive, enabled = enabled, onDaysActiveChange = onDaysActiveChange)
            if (validationField == "reminder_days") {
                Text(
                    stringResource(R.string.reminder_validation_no_active_day),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // @spec REM-UI-008
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.reminder_show_category_in_notification),
                    modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                )
                Switch(
                    checked = showCategoryInNotification,
                    enabled = enabled,
                    onCheckedChange = onShowCategoryInNotificationChange,
                )
            }
        }
    }
}

// @spec REM-UI-004
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimesEditor(
    times: List<LocalTime>,
    // @spec CAT-UI-018
    enabled: Boolean,
    onTimesChange: (List<LocalTime>) -> Unit,
) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(times.size) { index ->
            InputChip(
                selected = false,
                enabled = enabled,
                onClick = { editingIndex = index },
                label = { Text(formatTime(times[index])) },
                trailingIcon = if (times.size > 1) {
                    {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(enabled = enabled) {
                                    onTimesChange(times.filterIndexed { i, _ -> i != index })
                                },
                        )
                    }
                } else null,
            )
        }
        item {
            TextButton(onClick = { showAddPicker = true }, enabled = enabled) {
                Text(stringResource(R.string.reminder_add_time))
            }
        }
    }

    editingIndex?.let { index ->
        val state = rememberTimePickerState(initialHour = times[index].hour, initialMinute = times[index].minute)
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    onTimesChange(times.mapIndexed { i, t -> if (i == index) LocalTime.of(state.hour, state.minute) else t })
                    editingIndex = null
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { editingIndex = null }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }

    if (showAddPicker) {
        val state = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { showAddPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimesChange(times + LocalTime.of(state.hour, state.minute))
                    showAddPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showAddPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

// @spec REM-UI-005
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerFieldButton(
    label: String,
    time: LocalTime,
    // @spec CAT-UI-018
    enabled: Boolean,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, enabled = enabled, modifier = modifier) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatTime(time))
        }
    }
    if (showPicker) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

// @spec REM-UI-007
@Composable
private fun DaysOfWeekRow(
    daysActive: Set<DayOfWeek>,
    // @spec CAT-UI-018
    enabled: Boolean,
    onDaysActiveChange: (Set<DayOfWeek>) -> Unit,
) {
    val days = listOf(
        DayOfWeek.MONDAY to R.string.reminder_day_mon,
        DayOfWeek.TUESDAY to R.string.reminder_day_tue,
        DayOfWeek.WEDNESDAY to R.string.reminder_day_wed,
        DayOfWeek.THURSDAY to R.string.reminder_day_thu,
        DayOfWeek.FRIDAY to R.string.reminder_day_fri,
        DayOfWeek.SATURDAY to R.string.reminder_day_sat,
        DayOfWeek.SUNDAY to R.string.reminder_day_sun,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(days) { (day, labelRes) ->
            FilterChip(
                selected = day in daysActive,
                enabled = enabled,
                onClick = { onDaysActiveChange(if (day in daysActive) daysActive - day else daysActive + day) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
