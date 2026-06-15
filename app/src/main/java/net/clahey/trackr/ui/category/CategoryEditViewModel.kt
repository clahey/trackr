package net.clahey.trackr.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.EventValue
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.matchesValueType
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import net.clahey.trackr.ui.SaveResult
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

enum class ValueTypeWarningTier { IrreversibleSafe, Partial, Unsafe }

enum class EmojiMode { INHERIT, CUSTOM }
data class EmojiUIState(val mode: EmojiMode, val customValue: String)

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-054, CAT-UI-062, CAT-NAV-005, CAT-NAV-006, CAT-UI-067, DM-PROC-021, APP-NAV-004
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val categoryId: String? = savedStateHandle["categoryId"]
    private val parentId: String? = savedStateHandle["parentId"]

    val isEditMode: Boolean get() = categoryId != null

    // @spec CAT-UI-067
    private val _isDirty = MutableStateFlow(categoryId == null)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    fun setName(value: String) { _name.value = value; _isDirty.value = true }

    // @spec CAT-UI-054
    private val _emojiUIState = MutableStateFlow(EmojiUIState(EmojiMode.INHERIT, ""))
    val emojiUIState: StateFlow<EmojiUIState> = _emojiUIState.asStateFlow()
    fun setEmojiUIState(value: EmojiUIState) { _emojiUIState.value = value; _isDirty.value = true }

    private val _colorState = MutableStateFlow<Long?>(null)
    val colorState: StateFlow<Long?> = _colorState.asStateFlow()
    fun setColorState(value: Long?) { _colorState.value = value; _isDirty.value = true }

    private val _valueTypeState = MutableStateFlow<ValueType?>(null)
    val valueTypeState: StateFlow<ValueType?> = _valueTypeState.asStateFlow()
    fun setValueTypeState(value: ValueType?) { _valueTypeState.value = value; _isDirty.value = true }

    private val _numberDefaultUnit = MutableStateFlow("")
    val numberDefaultUnit: StateFlow<String> = _numberDefaultUnit.asStateFlow()

    private val _exerciseDefaultSets = MutableStateFlow("3")
    val exerciseDefaultSets: StateFlow<String> = _exerciseDefaultSets.asStateFlow()

    private val _exerciseDefaultReps = MutableStateFlow("15")
    val exerciseDefaultReps: StateFlow<String> = _exerciseDefaultReps.asStateFlow()

    private var defaultValueDirty = false

    fun updateNumberDefaultUnit(value: String) { _numberDefaultUnit.value = value; defaultValueDirty = true; _isDirty.value = true }
    fun updateExerciseDefaultSets(value: String) { _exerciseDefaultSets.value = value; defaultValueDirty = true; _isDirty.value = true }
    fun updateExerciseDefaultReps(value: String) { _exerciseDefaultReps.value = value; defaultValueDirty = true; _isDirty.value = true }
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

    // Live event counts in edit mode; zero in create mode.
    val ownEventCount: StateFlow<Int> = if (categoryId != null) {
        repository.getEventCountForCategory(categoryId, includeSubCategoriesWithNullType = false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    } else MutableStateFlow(0)

    val subCategoryCount: StateFlow<Int> = if (categoryId != null) {
        repository.getSubCategoryCount(categoryId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    } else MutableStateFlow(0)

    init {
        when {
            categoryId != null -> {
                viewModelScope.launch {
                    val cat = repository.getCategoryById(categoryId).first()
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
            }

            parentId != null -> {
                // SubCategory create mode. Do NOT advance color counter (CAT-UI-043).
                viewModelScope.launch {
                    val parent = repository.getCategoryById(parentId).first()
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
                }
            }
        }
    }

    suspend fun save() {
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

        val originalType = _originalValueType.value
        // @spec DM-PROC-021
        if (categoryId != null && originalType != null && category.resolvedValueType != originalType) {
            repository.saveCategoryAndMigrateEvents(category, originalType)
        } else {
            repository.saveCategory(category)
        }
        _isDirty.value = false
        _saveResult.value = SaveResult.Success
    }

    // @spec CAT-UI-004, CAT-UI-005, CAT-NAV-005
    fun requestDelete() {
        val id = categoryId ?: return
        val confirmation = deletionConfirmationIfNeeded(id, ownEventCount.value, subCategoryCount.value)
        if (confirmation == null) {
            viewModelScope.launch {
                repository.deleteCategory(id)
                _saveResult.value = SaveResult.Success
            }
        } else {
            _pendingDeleteConfirmation.value = confirmation
        }
    }

    fun confirmDelete() {
        val pending = _pendingDeleteConfirmation.value ?: return
        viewModelScope.launch {
            repository.deleteCategory(pending.categoryId)
            _pendingDeleteConfirmation.value = null
            _saveResult.value = SaveResult.Success
        }
    }

    fun cancelDelete() {
        _pendingDeleteConfirmation.value = null
    }

    // @spec CAT-UI-030, CAT-UI-036, CAT-UI-037, CAT-UI-038
    private fun warningTierFor(from: ValueType, to: ValueType): ValueTypeWarningTier? = when {
        // Reversible pairs → no warning
        (from == ValueType.None && to == ValueType.Text) ||
        (from == ValueType.Scale && to == ValueType.Text) ||
        (from == ValueType.Boolean && to == ValueType.Text) ||
        (from == ValueType.Number && to == ValueType.Text) ||
        (from == ValueType.Exercise && to == ValueType.Text) -> null
        // Fully safe but irreversible
        from == ValueType.None ||
        (from == ValueType.Scale && to == ValueType.Number) ||
        (from == ValueType.Duration && to == ValueType.Text) -> ValueTypeWarningTier.IrreversibleSafe
        // Partially safe: migration attempted but some events may not convert
        from == ValueType.Text && to in listOf(
            ValueType.Boolean, ValueType.Number, ValueType.Scale, ValueType.None, ValueType.Exercise,
        ) -> ValueTypeWarningTier.Partial
        // All other pairs: no migration
        else -> ValueTypeWarningTier.Unsafe
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
