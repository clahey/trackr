package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.Reminder
import net.clahey.trackr.domain.ReminderMode
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.matchesValueType
import net.clahey.trackr.domain.ValueTypeWarningTier
import net.clahey.trackr.domain.warningTierFor
import net.clahey.trackr.reminders.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.ReminderPermissionProblem
import net.clahey.trackr.ui.components.reminderPermissionProblem
import net.clahey.trackr.ui.theme.DEFAULT_CATEGORY_COLOR
import net.clahey.trackr.ui.theme.categoryColorForIndex
import net.clahey.trackr.ui.theme.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class EmojiMode { INHERIT, CUSTOM }
data class EmojiUIState(val mode: EmojiMode, val customValue: String)

data class ReminderUIState(
    val enabled: Boolean,
    val mode: ReminderMode,
    val times: List<LocalTime>,
    val windowStart: LocalTime,
    val windowEnd: LocalTime,
    val occurrencesPerDay: String,
    val daysActive: Set<DayOfWeek>,
    val showCategoryInNotification: Boolean,
) {
    // @spec REM-UI-006a, REM-UI-009, REM-UI-010
    fun validationError(): String? {
        if (!enabled) return null
        if (daysActive.isEmpty()) return "reminder_days"
        return when (mode) {
            ReminderMode.FIXED -> if (times.isEmpty()) "reminder_times" else null
            ReminderMode.RANDOM -> {
                val validWindow = windowEnd == LocalTime.MIDNIGHT || windowEnd.isAfter(windowStart)
                val occurrences = occurrencesPerDay.toIntOrNull() ?: 0
                when {
                    !validWindow -> "reminder_window"
                    occurrences < 1 -> "reminder_occurrences"
                    else -> null
                }
            }
        }
    }

    // @spec REM-DATA-002, REM-DATA-006, REM-DATA-008
    fun toReminder(categoryId: String): Reminder = Reminder(
        categoryId = categoryId,
        enabled = enabled,
        mode = mode,
        times = times,
        windowStart = windowStart,
        windowEnd = windowEnd,
        occurrencesPerDay = occurrencesPerDay.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        daysActive = daysActive,
        showCategoryInNotification = showCategoryInNotification,
        nextFireAt = null, // ignored by saveCategoryWithReminder; the DB's current value survives (REM-DATA-008)
    )

    companion object {
        // @spec REM-UI-001
        fun fromStored(reminder: Reminder): ReminderUIState = ReminderUIState(
            enabled = reminder.enabled,
            mode = reminder.mode,
            times = reminder.times.ifEmpty { listOf(LocalTime.of(9, 0)) },
            windowStart = reminder.windowStart,
            windowEnd = reminder.windowEnd,
            occurrencesPerDay = reminder.occurrencesPerDay.toString(),
            daysActive = reminder.daysActive.ifEmpty { DayOfWeek.entries.toSet() },
            showCategoryInNotification = reminder.showCategoryInNotification,
        )
    }
}

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-054, CAT-UI-062, CAT-NAV-005, CAT-NAV-006, CAT-UI-067, DM-PROC-021, APP-NAV-004,
// REM-UI-001, REM-UI-011, REM-PERM-003, REM-DATA-006
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val reminderScheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val categoryId: String? = savedStateHandle["categoryId"]
    private val parentId: String? = savedStateHandle["parentId"]

    val isEditMode: Boolean get() = categoryId != null

    // @spec CAT-UI-067 — create mode is "dirty" from the start so the Save button shows immediately
    private val _isDirty = MutableStateFlow(categoryId == null)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    // @spec CAT-NAV-006 — tracks whether the user has actually edited a field; drives the
    // unsaved-changes back-guard, so an untouched new-category screen doesn't warn on immediate back.
    private val _hasUserEdits = MutableStateFlow(false)
    val hasUserEdits: StateFlow<Boolean> = _hasUserEdits.asStateFlow()

    // @spec CAT-UI-018 — one flag per initial-state read, seeded true when the current mode doesn't
    // issue that read (these mirror init's `when` branches). Each is flipped on the read's
    // *completion*, not its success, so a category with no reminder row still opens the gate.
    private val _categoryLoaded = MutableStateFlow(categoryId == null)
    private val _reminderLoaded = MutableStateFlow(categoryId == null)
    private val _parentLoaded = MutableStateFlow(categoryId != null || parentId == null)
    private val _colorLoaded = MutableStateFlow(categoryId != null || parentId != null)

    // @spec CAT-UI-018
    val isLoaded: StateFlow<Boolean> =
        combine(_categoryLoaded, _reminderLoaded, _parentLoaded, _colorLoaded) { c, r, p, col ->
            c && r && p && col
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun markEdited() {
        _isDirty.value = true
        _hasUserEdits.value = true
    }

    // Every field setter routes through here so the CAT-UI-018 gate can't be forgotten on a new
    // one: seeding writes state directly, so an edit accepted before the seed lands would be
    // silently overwritten by it.
    // @spec CAT-UI-018
    private inline fun edit(block: () -> Unit) {
        if (!isLoaded.value) return
        block()
        markEdited()
    }

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    fun setName(value: String) = edit { _name.value = value }

    // @spec CAT-UI-054
    private val _emojiUIState = MutableStateFlow(EmojiUIState(EmojiMode.INHERIT, ""))
    val emojiUIState: StateFlow<EmojiUIState> = _emojiUIState.asStateFlow()
    fun setEmojiUIState(value: EmojiUIState) = edit { _emojiUIState.value = value }

    private val _colorState = MutableStateFlow<Long?>(null)
    val colorState: StateFlow<Long?> = _colorState.asStateFlow()
    fun setColorState(value: Long?) = edit { _colorState.value = value }

    private val _valueTypeState = MutableStateFlow<ValueType?>(null)
    val valueTypeState: StateFlow<ValueType?> = _valueTypeState.asStateFlow()
    fun setValueTypeState(value: ValueType?) = edit { _valueTypeState.value = value }

    private val _numberDefaultUnit = MutableStateFlow("")
    val numberDefaultUnit: StateFlow<String> = _numberDefaultUnit.asStateFlow()

    private val _exerciseDefaultSets = MutableStateFlow("3")
    val exerciseDefaultSets: StateFlow<String> = _exerciseDefaultSets.asStateFlow()

    private val _exerciseDefaultReps = MutableStateFlow("15")
    val exerciseDefaultReps: StateFlow<String> = _exerciseDefaultReps.asStateFlow()

    private var defaultValueDirty = false

    fun updateNumberDefaultUnit(value: String) = edit { _numberDefaultUnit.value = value; defaultValueDirty = true }
    fun updateExerciseDefaultSets(value: String) = edit { _exerciseDefaultSets.value = value; defaultValueDirty = true }
    fun updateExerciseDefaultReps(value: String) = edit { _exerciseDefaultReps.value = value; defaultValueDirty = true }
    private var storedDefaultValue: EventValue? = null

    private val _parentCategory = MutableStateFlow<Category.MetaCategory?>(null)
    val parentCategory: StateFlow<Category.MetaCategory?> = _parentCategory.asStateFlow()

    val effectiveEmoji: StateFlow<String> = combine(_emojiUIState, _parentCategory) { state, parent ->
        if (state.mode == EmojiMode.INHERIT) parent?.emoji ?: "" else state.customValue
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val effectiveColor: StateFlow<Long> = combine(_colorState, _parentCategory) { c, parent ->
        c ?: parent?.color ?: DEFAULT_CATEGORY_COLOR
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CATEGORY_COLOR)

    val effectiveValueType: StateFlow<ValueType> = combine(_valueTypeState, _parentCategory) { v, parent ->
        v ?: parent?.valueType ?: ValueType.None
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ValueType.None)

    // @spec CAT-UI-059
    val previewEventValue: StateFlow<EventValue?> = combine(
        effectiveValueType, _numberDefaultUnit, _exerciseDefaultSets, _exerciseDefaultReps,
    ) { vt, unit, sets, reps ->
        val liveDefault = when (vt) {
            ValueType.Number -> EventValue.NumberValue(
                storedDefaultValue?.let { (it as? EventValue.NumberValue)?.value } ?: 0.0,
                unit.takeIf { it.isNotBlank() },
            )
            ValueType.Exercise -> EventValue.ExerciseValue(
                sets.toIntOrNull() ?: 3, reps.toIntOrNull() ?: 15,
            )
            else -> storedDefaultValue?.takeIf { matchesValueType(it, vt) }
        }
        liveDefault ?: when (vt) {
            ValueType.None -> null
            ValueType.Scale -> EventValue.Scale(7)
            ValueType.Boolean -> EventValue.BooleanValue(true)
            ValueType.Text -> EventValue.TextValue("Sample")
            ValueType.Duration -> EventValue.DurationValue(90.seconds)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val previewTimestamp = ZonedDateTime.now(ZoneId.systemDefault())
        .withHour(12).withMinute(0).withSecond(0).withNano(0).toInstant()

    val previewCategory: StateFlow<Category.MetaCategory> = combine(
        name, effectiveEmoji, effectiveColor, effectiveValueType, previewEventValue,
    ) { n, emoji, color, vt, dv ->
        Category.MetaCategory(
            id = "", name = n.ifEmpty { "Category name" }, emoji = emoji.ifEmpty { " " },
            color = color, valueType = vt, defaultValue = dv,
            allowEmptyText = true, sortOrder = 0,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Category.MetaCategory(
        id = "", name = "Category name", emoji = " ", color = DEFAULT_CATEGORY_COLOR,
        valueType = ValueType.None, defaultValue = null, allowEmptyText = true, sortOrder = 0,
    ))

    val previewEvent: StateFlow<Event> = previewEventValue.map { value ->
        Event(
            id = "", categoryId = "", timestamp = previewTimestamp,
            value = value, notes = "Notes", imagePaths = emptyList(), createdAt = previewTimestamp,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Event(
        id = "", categoryId = "", timestamp = previewTimestamp,
        value = null, notes = "Notes", imagePaths = emptyList(), createdAt = previewTimestamp,
    ))

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private val _pendingDeleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val pendingDeleteConfirmation: StateFlow<DeleteConfirmation?> = _pendingDeleteConfirmation.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    // @spec CAT-NAV-020
    private val _savedCategoryId = MutableStateFlow<String?>(null)
    val savedCategoryId: StateFlow<String?> = _savedCategoryId.asStateFlow()

    private val _originalValueType = MutableStateFlow<ValueType?>(null)

    // @spec CAT-UI-030
    val valueTypeWarning: StateFlow<ValueTypeWarningTier?> = if (categoryId != null) {
        combine(
            effectiveValueType,
            _originalValueType,
            repository.getEventCountForCategory(categoryId, includeSubCategoriesWithNullType = true),
        ) { type, orig, count ->
            if (orig == null || type == orig || count == 0) null
            else warningTierFor(orig, type)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    } else MutableStateFlow(null)

    private val _reminderUIState = MutableStateFlow(ReminderUIState.fromStored(Reminder.default("")))
    val reminderUIState: StateFlow<ReminderUIState> = _reminderUIState.asStateFlow()

    fun setReminderEnabled(value: Boolean) = edit { _reminderUIState.value = _reminderUIState.value.copy(enabled = value) }
    fun setReminderMode(value: ReminderMode) = edit { _reminderUIState.value = _reminderUIState.value.copy(mode = value) }
    fun setReminderTimes(value: List<LocalTime>) = edit { _reminderUIState.value = _reminderUIState.value.copy(times = value) }
    fun setReminderWindowStart(value: LocalTime) = edit { _reminderUIState.value = _reminderUIState.value.copy(windowStart = value) }
    fun setReminderWindowEnd(value: LocalTime) = edit { _reminderUIState.value = _reminderUIState.value.copy(windowEnd = value) }
    // The check wraps `edit` rather than sitting inside it: `edit` marks the form dirty, and a
    // declined keystroke changed nothing.
    // @spec REM-UI-006
    fun setReminderOccurrencesPerDay(value: String) {
        if (value.length <= 2 && value.all { it.isDigit() }) {
            edit { _reminderUIState.value = _reminderUIState.value.copy(occurrencesPerDay = value) }
        }
    }
    fun setReminderDaysActive(value: Set<DayOfWeek>) = edit { _reminderUIState.value = _reminderUIState.value.copy(daysActive = value) }
    fun setReminderShowCategoryInNotification(value: Boolean) = edit { _reminderUIState.value = _reminderUIState.value.copy(showCategoryInNotification = value) }

    // @spec REM-PERM-003
    private val _pendingPermissionConfirmation = MutableStateFlow<ReminderPermissionProblem?>(null)
    val pendingPermissionConfirmation: StateFlow<ReminderPermissionProblem?> =
        _pendingPermissionConfirmation.asStateFlow()
    fun dismissPermissionConfirmation() { _pendingPermissionConfirmation.value = null }

    init {
        when {
            categoryId != null -> {
                viewModelScope.launch {
                    val cat = repository.getCategoryById(categoryId).first()
                    // @spec CAT-UI-018 — the not-found navigation below is not gated; it fires as
                    // soon as this read resolves, without waiting on the reminder read.
                    _categoryLoaded.value = true
                    if (cat == null) { _navigateBack.value = true; return@launch }
                    _name.value = cat.name
                    storedDefaultValue = cat.defaultValue
                    seedDefaultValueFields(cat.defaultValue, cat.resolvedValueType)
                    when (cat) {
                        is Category.MetaCategory -> {
                            _emojiUIState.value = EmojiUIState(EmojiMode.CUSTOM, cat.emoji)
                            _colorState.value = cat.color
                            _valueTypeState.value = cat.valueType
                        }
                        is Category.SubCategory -> {
                            // @spec CAT-UI-062
                            _emojiUIState.value = if (cat.emoji != null) {
                                EmojiUIState(EmojiMode.CUSTOM, cat.emoji)
                            } else {
                                EmojiUIState(EmojiMode.INHERIT, cat.parent.emoji)
                            }
                            _colorState.value = cat.color
                            _valueTypeState.value = cat.valueType
                            _parentCategory.value = cat.parent
                        }
                    }
                    _originalValueType.value = cat.resolvedValueType
                }
                // @spec REM-UI-001, CAT-UI-018
                viewModelScope.launch {
                    repository.getReminderForCategory(categoryId).first()?.let {
                        _reminderUIState.value = ReminderUIState.fromStored(it)
                    }
                    _reminderLoaded.value = true
                }
            }

            parentId != null -> {
                // SubCategory create mode. Do NOT advance color counter (CAT-UI-043).
                viewModelScope.launch {
                    val parent = repository.getCategoryById(parentId).first()
                    // @spec CAT-UI-018
                    _parentLoaded.value = true
                    if (parent is Category.MetaCategory) {
                        _parentCategory.value = parent
                        // @spec CAT-UI-062
                        _emojiUIState.value = EmojiUIState(EmojiMode.INHERIT, parent.emoji)
                        // @spec CAT-UI-066 — pre-populate from parent's resolved default; don't mark dirty
                        seedDefaultValueFields(parent.resolvedDefaultValue, parent.resolvedValueType)
                    } else {
                        _navigateBack.value = true
                    }
                }
            }

            else -> {
                // MetaCategory create mode.
                _emojiUIState.value = EmojiUIState(EmojiMode.CUSTOM, "")
                _valueTypeState.value = ValueType.None
                // @spec CAT-UI-043
                viewModelScope.launch {
                    _colorState.value = categoryColorForIndex(
                        repository.getAndIncrementNextCategoryColorIndex(categoryColorPalette.size)
                    )
                    // @spec CAT-UI-018
                    _colorLoaded.value = true
                }
            }
        }
    }

    // @spec REM-UI-009, REM-UI-010, REM-PERM-003
    suspend fun save(
        notificationPermissionGranted: Boolean = true,
        reminderChannelEnabled: Boolean = true,
        forceSaveDespitePermission: Boolean = false,
    ) {
        // @spec CAT-UI-018 — saving before the seed reads land would persist defaults over stored
        // values: a MetaCategory over a SubCategory whose parent hadn't loaded, or a disabled
        // default reminder over a configured one.
        if (!isLoaded.value) return
        val nameVal = _name.value.trim()
        if (nameVal.isEmpty()) { _saveResult.value = SaveResult.ValidationError("name"); return }

        val parent = _parentCategory.value
        val emojiStateVal = _emojiUIState.value
        val emojiToSave: String? = if (parent != null && emojiStateVal.mode == EmojiMode.INHERIT) null else emojiStateVal.customValue
        if (emojiToSave != null) {
            if (emojiToSave.isEmpty()) { _saveResult.value = SaveResult.ValidationError("emoji"); return }
            if (emojiToSave.graphemeClusterCount() != 1) {
                _saveResult.value = SaveResult.ValidationError("emoji"); return
            }
        } else if (parent == null) {
            _saveResult.value = SaveResult.ValidationError("emoji"); return
        }

        // @spec REM-UI-009
        val reminderValidationError = _reminderUIState.value.validationError()
        if (reminderValidationError != null) {
            _saveResult.value = SaveResult.ValidationError(reminderValidationError)
            return
        }

        // @spec REM-PERM-003, REM-SCHED-021
        val permissionProblem = reminderPermissionProblem(
            notificationsEnabled = notificationPermissionGranted,
            reminderChannelEnabled = reminderChannelEnabled,
            exactAlarmAvailable = reminderScheduler.canScheduleExact(),
        )
        if (_reminderUIState.value.enabled && !forceSaveDespitePermission && permissionProblem != null) {
            _pendingPermissionConfirmation.value = permissionProblem
            return
        }
        _pendingPermissionConfirmation.value = null

        val sortOrder = categoryId?.let { repository.getCategoryById(it).first()?.sortOrder }
            ?: (repository.getCategories().first().minOfOrNull { it.sortOrder }?.minus(1) ?: 0)

        // @spec CAT-UI-063, CAT-UI-064, CAT-UI-065, CAT-UI-066
        val effectiveVt = effectiveValueType.value
        val defaultValueToSave: EventValue? = when {
            !defaultValueDirty -> storedDefaultValue  // CAT-UI-066: preserve unchanged when user hasn't edited fields
            effectiveVt == ValueType.Number -> EventValue.NumberValue(
                (storedDefaultValue as? EventValue.NumberValue)?.value ?: 0.0,
                _numberDefaultUnit.value.takeIf { it.isNotBlank() },
            )
            effectiveVt == ValueType.Exercise -> {
                val sets = _exerciseDefaultSets.value.toIntOrNull() ?: 3
                val reps = _exerciseDefaultReps.value.toIntOrNull() ?: 15
                EventValue.ExerciseValue(sets, reps)
            }
            else -> storedDefaultValue  // CAT-UI-065: leave unchanged
        }

        val category: Category = if (parent != null) {
            // @spec CAT-UI-041
            Category.SubCategory(
                id = categoryId ?: UUID.randomUUID().toString(),
                name = nameVal,
                emoji = emojiToSave,
                color = _colorState.value,
                valueType = _valueTypeState.value,
                defaultValue = defaultValueToSave,
                allowEmptyText = true,
                sortOrder = sortOrder,
                parent = parent,
            )
        } else {
            Category.MetaCategory(
                id = categoryId ?: UUID.randomUUID().toString(),
                name = nameVal,
                emoji = emojiToSave ?: "",
                color = _colorState.value ?: DEFAULT_CATEGORY_COLOR,
                valueType = _valueTypeState.value ?: ValueType.None,
                defaultValue = defaultValueToSave,
                allowEmptyText = true,
                sortOrder = sortOrder,
            )
        }

        val reminder = _reminderUIState.value.toReminder(category.id)

        val originalType = _originalValueType.value
        // @spec DM-PROC-021
        if (categoryId != null && originalType != null && category.resolvedValueType != originalType) {
            repository.saveCategoryWithReminder(category, reminder, migrateFromType = originalType)
        } else {
            repository.saveCategoryWithReminder(category, reminder)
        }
        // @spec REM-SCHED-013, REM-SCHED-014
        if (reminder.enabled) reminderScheduler.enableReminder(reminder) else reminderScheduler.disableReminder(category.id)

        _isDirty.value = false
        // @spec CAT-NAV-020
        _savedCategoryId.value = category.id
        _saveResult.value = SaveResult.Success
    }

    // The counts are read here rather than held as state: a cached count seeds at 0, and 0/0 is exactly
    // the condition for deleting without a dialog, so a tap arriving before the queries emitted would
    // destroy a populated category silently.
    // @spec CAT-UI-004, CAT-UI-005, CAT-UI-019, CAT-NAV-005
    fun requestDelete() {
        val id = categoryId ?: return
        viewModelScope.launch {
            val ownEventCount =
                repository.getEventCountForCategory(id, includeSubCategoriesWithNullType = false).first()
            val subCategoryCount = repository.getSubCategoryCount(id).first()
            val confirmation = deletionConfirmationIfNeeded(id, ownEventCount, subCategoryCount)
            if (confirmation == null) performDelete(id) else _pendingDeleteConfirmation.value = confirmation
        }
    }

    // @spec CAT-UI-007
    fun confirmDelete() {
        val pending = _pendingDeleteConfirmation.value ?: return
        viewModelScope.launch { performDelete(pending.categoryId) }
    }

    // Both delete paths end here so the alarm cancel can't be wired into one and forgotten on the other.
    // @spec CAT-UI-006, CAT-UI-007
    private suspend fun performDelete(id: String) {
        repository.deleteCategory(id)
        reminderScheduler.cancel(id)
        _pendingDeleteConfirmation.value = null
        _saveResult.value = SaveResult.Success
    }

    fun cancelDelete() {
        _pendingDeleteConfirmation.value = null
    }

    // @spec CAT-UI-011, CAT-UI-011a, CAT-UI-066
    private fun seedDefaultValueFields(dv: EventValue?, effectiveType: ValueType) {
        when {
            effectiveType == ValueType.Number -> {
                _numberDefaultUnit.value = (dv as? EventValue.NumberValue)?.unit ?: ""
            }
            effectiveType == ValueType.Exercise -> {
                val ev = dv as? EventValue.ExerciseValue
                _exerciseDefaultSets.value = ev?.sets?.toString() ?: "3"
                _exerciseDefaultReps.value = ev?.reps?.toString() ?: "15"
            }
        }
    }

    private fun String.graphemeClusterCount(): Int {
        val bi = java.text.BreakIterator.getCharacterInstance()
        bi.setText(this)
        var count = 0
        while (bi.next() != java.text.BreakIterator.DONE) count++
        return count
    }
}
