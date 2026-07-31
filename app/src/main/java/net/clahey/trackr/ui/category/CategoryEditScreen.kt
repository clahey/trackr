package net.clahey.trackr.ui.category

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.Card
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

    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()
    val scope = rememberCoroutineScope()
    var showBackDiscardDialog by remember { mutableStateOf(false) }

    // @spec REM-UI-001..011, REM-PERM-001..004
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val reminderMode by viewModel.reminderMode.collectAsState()
    val reminderTimes by viewModel.reminderTimes.collectAsState()
    val reminderWindowStart by viewModel.reminderWindowStart.collectAsState()
    val reminderWindowEnd by viewModel.reminderWindowEnd.collectAsState()
    val reminderOccurrencesPerDay by viewModel.reminderOccurrencesPerDay.collectAsState()
    val reminderDaysActive by viewModel.reminderDaysActive.collectAsState()
    val reminderShowCategoryInNotification by viewModel.reminderShowCategoryInNotification.collectAsState()
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
    if (pendingPermissionConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionConfirmation() },
            title = { Text(stringResource(R.string.reminder_permission_dialog_title)) },
            text = { Text(stringResource(R.string.reminder_permission_dialog_message)) },
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
                isError = (saveResult as? SaveResult.ValidationError)?.field == "name",
            )

            val emojiIsError = (saveResult as? SaveResult.ValidationError)?.field == "emoji"
            // @spec CAT-UI-074, CAT-UI-076
            OutlinedFieldBox(label = stringResource(R.string.category_field_emoji), isError = emojiIsError) {
                EmojiField(
                    emojiUIState = emojiUIState,
                    parentEmoji = parentCategory?.emoji,
                    isError = emojiIsError,
                    onUIStateChange = { viewModel.setEmojiUIState(it) },
                )
            }

            // @spec CAT-UI-056, CAT-UI-075
            OutlinedFieldBox(label = stringResource(R.string.category_field_color)) {
                ColorPicker(
                    colorState = colorState,
                    parentCategory = parentCategory,
                    effectiveColor = effectiveColor,
                    isColorInherited = isColorInherited,
                    onSelectColor = { viewModel.setColorState(it) },
                )
            }

            // @spec CAT-UI-057
            ValueTypeSelector(
                selected = effectiveValueType,
                isValueTypeInherited = isValueTypeInherited,
                parentCategory = parentCategory,
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

            // @spec REM-UI-001..011, REM-PERM-001, REM-PERM-002
            ReminderSection(
                enabled = reminderEnabled,
                mode = reminderMode,
                times = reminderTimes,
                windowStart = reminderWindowStart,
                windowEnd = reminderWindowEnd,
                occurrencesPerDay = reminderOccurrencesPerDay,
                daysActive = reminderDaysActive,
                showCategoryInNotification = reminderShowCategoryInNotification,
                validationField = (saveResult as? SaveResult.ValidationError)?.field,
                onEnabledChange = { viewModel.setReminderEnabled(it) },
                onModeChange = { viewModel.setReminderMode(it) },
                onTimesChange = { viewModel.setReminderTimes(it) },
                onWindowStartChange = { viewModel.setReminderWindowStart(it) },
                onWindowEndChange = { viewModel.setReminderWindowEnd(it) },
                onOccurrencesPerDayChange = { viewModel.setReminderOccurrencesPerDay(it) },
                onDaysActiveChange = { viewModel.setReminderDaysActive(it) },
                onShowCategoryInNotificationChange = { viewModel.setReminderShowCategoryInNotification(it) },
            )

            // @spec CAT-UI-067
            if (isDirty) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { doSave() },
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
                        onValueChange = { checked ->
                            onUIStateChange(emojiUIState.copy(
                                mode = if (checked) EmojiMode.INHERIT else EmojiMode.CUSTOM,
                            ))
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.category_emoji_inherit))
                Text(
                    text = parentEmoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.alpha(if (isInherited) 1f else 0.38f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = isInherited, onCheckedChange = null)
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
                        .clickable { onUIStateChange(EmojiUIState(EmojiMode.CUSTOM, emoji)) },
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
            OutlinedButton(onClick = { showPicker = true }) {
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

// @spec CAT-UI-014, CAT-UI-015, CAT-UI-016
@SuppressLint("UnusedBoxWithConstraintsScope")
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
// @spec CAT-UI-047
@Composable
private fun ValueTypeSelector(
    selected: ValueType,
    isValueTypeInherited: Boolean,
    parentCategory: Category.MetaCategory?,
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

private fun formatTime(time: LocalTime): String = time.format(DateTimeFormatter.ofPattern("h:mm a"))

// @spec REM-UI-001, REM-UI-002, REM-UI-003, REM-UI-007, REM-UI-008, REM-UI-009, REM-UI-010,
// REM-UI-011, REM-PERM-001, REM-PERM-002
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(
    enabled: Boolean,
    mode: ReminderMode,
    times: List<LocalTime>,
    windowStart: LocalTime,
    windowEnd: LocalTime,
    occurrencesPerDay: Int,
    daysActive: Set<DayOfWeek>,
    showCategoryInNotification: Boolean,
    validationField: String?,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (ReminderMode) -> Unit,
    onTimesChange: (List<LocalTime>) -> Unit,
    onWindowStartChange: (LocalTime) -> Unit,
    onWindowEndChange: (LocalTime) -> Unit,
    onOccurrencesPerDayChange: (Int) -> Unit,
    onDaysActiveChange: (Set<DayOfWeek>) -> Unit,
    onShowCategoryInNotificationChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* live-checked elsewhere via NotificationManagerCompat; nothing to store */ }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text(stringResource(R.string.reminder_section_title))
            }
            // @spec REM-PERM-001
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    onEnabledChange(checked)
                    if (checked) requestNotificationPermissionIfNeeded()
                },
            )
        }

        // @spec REM-UI-003
        if (enabled) {
            // Fires once each time this content is entered — turning the section on, or
            // reopening an already-on one — matching "entering/expanding the Reminder section".
            LaunchedEffect(Unit) { requestNotificationPermissionIfNeeded() }

            // @spec REM-PERM-002
            val exactAlarmAvailable = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            if (!exactAlarmAvailable) {
                Card {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.reminder_exact_alarm_prompt), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }) { Text(stringResource(R.string.reminder_exact_alarm_open_settings)) }
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ReminderMode.FIXED,
                    onClick = { onModeChange(ReminderMode.FIXED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.reminder_mode_fixed)) }
                SegmentedButton(
                    selected = mode == ReminderMode.RANDOM,
                    onClick = { onModeChange(ReminderMode.RANDOM) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.reminder_mode_random)) }
            }

            if (mode == ReminderMode.FIXED) {
                // @spec REM-UI-004
                ReminderTimesEditor(times = times, onTimesChange = onTimesChange)
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
                        onTimeChange = onWindowStartChange,
                        modifier = Modifier.weight(1f),
                    )
                    TimePickerFieldButton(
                        label = stringResource(R.string.reminder_window_end),
                        time = windowEnd,
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
            Text(stringResource(R.string.reminder_active_days), style = MaterialTheme.typography.labelMedium)
            DaysOfWeekRow(daysActive = daysActive, onDaysActiveChange = onDaysActiveChange)
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
                Text(stringResource(R.string.reminder_show_category_in_notification))
                Switch(checked = showCategoryInNotification, onCheckedChange = onShowCategoryInNotificationChange)
            }
        }
    }
}

// @spec REM-UI-004
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimesEditor(times: List<LocalTime>, onTimesChange: (List<LocalTime>) -> Unit) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddPicker by remember { mutableStateOf(false) }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(times.size) { index ->
            InputChip(
                selected = false,
                onClick = { editingIndex = index },
                label = { Text(formatTime(times[index])) },
                trailingIcon = if (times.size > 1) {
                    {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_remove),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onTimesChange(times.filterIndexed { i, _ -> i != index }) },
                        )
                    }
                } else null,
            )
        }
        item {
            TextButton(onClick = { showAddPicker = true }) { Text(stringResource(R.string.reminder_add_time)) }
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
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
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
private fun DaysOfWeekRow(daysActive: Set<DayOfWeek>, onDaysActiveChange: (Set<DayOfWeek>) -> Unit) {
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
                onClick = { onDaysActiveChange(if (day in daysActive) daysActive - day else daysActive + day) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}
