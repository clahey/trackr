package com.trackr.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.domain.matchesValueType
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.theme.DEFAULT_CATEGORY_COLOR
import com.trackr.app.ui.theme.categoryColorForIndex
import com.trackr.app.ui.theme.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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
// CAT-UI-054, CAT-UI-062, CAT-NAV-005, DM-PROC-021, APP-NAV-004
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val categoryId: String? = savedStateHandle["categoryId"]
    private val parentId: String? = savedStateHandle["parentId"]

    val isEditMode: Boolean get() = categoryId != null

    val name = MutableStateFlow("")

    // @spec CAT-UI-054
    val emojiUIState = MutableStateFlow(EmojiUIState(EmojiMode.INHERIT, ""))
    val colorState = MutableStateFlow<Long?>(null)
    val valueTypeState = MutableStateFlow<ValueType?>(null)

    val numberDefaultUnit = MutableStateFlow("")
    val exerciseDefaultSets = MutableStateFlow("3")
    val exerciseDefaultReps = MutableStateFlow("15")
    private var defaultValueDirty = false
    private var storedDefaultValue: EventValue? = null

    private val _parentCategory = MutableStateFlow<Category.MetaCategory?>(null)
    val parentCategory: StateFlow<Category.MetaCategory?> = _parentCategory.asStateFlow()

    val effectiveEmoji: StateFlow<String> = combine(emojiUIState, _parentCategory) { state, parent ->
        if (state.mode == EmojiMode.INHERIT) parent?.emoji ?: "" else state.customValue
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val effectiveColor: StateFlow<Long> = combine(colorState, _parentCategory) { c, parent ->
        c ?: parent?.color ?: DEFAULT_CATEGORY_COLOR
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CATEGORY_COLOR)

    val effectiveValueType: StateFlow<ValueType> = combine(valueTypeState, _parentCategory) { v, parent ->
        v ?: parent?.valueType ?: ValueType.None
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ValueType.None)

    val isColorInherited: StateFlow<Boolean> = colorState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isValueTypeInherited: StateFlow<Boolean> = valueTypeState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // @spec CAT-UI-059
    val previewEventValue: StateFlow<EventValue?> = combine(
        effectiveValueType, numberDefaultUnit, exerciseDefaultSets, exerciseDefaultReps,
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

    private val _valueTypeWarning = MutableStateFlow<ValueTypeWarningTier?>(null)
    val valueTypeWarning: StateFlow<ValueTypeWarningTier?> = _valueTypeWarning.asStateFlow()

    private val _originalValueType = MutableStateFlow<ValueType?>(null)

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
                    name.value = cat.name
                    storedDefaultValue = cat.defaultValue
                    seedDefaultValueFields(cat.defaultValue, cat.resolvedValueType)
                    when (cat) {
                        is Category.MetaCategory -> {
                            emojiUIState.value = EmojiUIState(EmojiMode.CUSTOM, cat.emoji)
                            colorState.value = cat.color
                            valueTypeState.value = cat.valueType
                        }
                        is Category.SubCategory -> {
                            // @spec CAT-UI-062
                            emojiUIState.value = if (cat.emoji != null) {
                                EmojiUIState(EmojiMode.CUSTOM, cat.emoji)
                            } else {
                                EmojiUIState(EmojiMode.INHERIT, cat.parent.emoji)
                            }
                            colorState.value = cat.color
                            valueTypeState.value = cat.valueType
                            _parentCategory.value = cat.parent
                        }
                    }
                    _originalValueType.value = cat.resolvedValueType

                    val includeInheriting = cat is Category.MetaCategory
                    viewModelScope.launch {
                        // @spec CAT-UI-030
                        combine(
                            effectiveValueType,
                            _originalValueType,
                            repository.getEventCountForCategory(
                                categoryId,
                                includeSubCategoriesWithNullType = includeInheriting,
                            ),
                        ) { type, orig, count ->
                            if (orig == null || type == orig || count == 0) null
                            else warningTierFor(orig, type)
                        }.collect { _valueTypeWarning.value = it }
                    }
                }
            }

            parentId != null -> {
                // SubCategory create mode. Do NOT advance color counter (CAT-UI-043).
                viewModelScope.launch {
                    val parent = repository.getCategoryById(parentId).first()
                    if (parent is Category.MetaCategory) {
                        _parentCategory.value = parent
                        // @spec CAT-UI-062
                        emojiUIState.value = EmojiUIState(EmojiMode.INHERIT, parent.emoji)
                        // @spec CAT-UI-066 — pre-populate from parent's resolved default; don't mark dirty
                        seedDefaultValueFields(parent.resolvedDefaultValue, parent.resolvedValueType)
                        // @spec CAT-UI-066 — drop(1) skips the initial emission of seeded values
                        viewModelScope.launch {
                            combine(numberDefaultUnit, exerciseDefaultSets, exerciseDefaultReps) { _, _, _ -> }
                                .drop(1)
                                .collect { defaultValueDirty = true }
                        }
                    } else {
                        _navigateBack.value = true
                    }
                }
            }

            else -> {
                // MetaCategory create mode.
                emojiUIState.value = EmojiUIState(EmojiMode.CUSTOM, "")
                valueTypeState.value = ValueType.None
                // @spec CAT-UI-043
                viewModelScope.launch {
                    colorState.value = categoryColorForIndex(
                        repository.getAndIncrementNextCategoryColorIndex(categoryColorPalette.size)
                    )
                }
            }
        }
    }

    suspend fun save() {
        val nameVal = name.value.trim()
        if (nameVal.isEmpty()) { _saveResult.value = SaveResult.ValidationError("name"); return }

        val parent = _parentCategory.value
        val emojiStateVal = emojiUIState.value
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
            parent != null && !defaultValueDirty -> null  // inherit; CAT-UI-066
            effectiveVt == ValueType.Number -> EventValue.NumberValue(
                (storedDefaultValue as? EventValue.NumberValue)?.value ?: 0.0,
                numberDefaultUnit.value.takeIf { it.isNotBlank() },
            )
            effectiveVt == ValueType.Exercise -> {
                val sets = exerciseDefaultSets.value.toIntOrNull() ?: 3
                val reps = exerciseDefaultReps.value.toIntOrNull() ?: 15
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
                color = colorState.value,
                valueType = valueTypeState.value,
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
                color = colorState.value ?: DEFAULT_CATEGORY_COLOR,
                valueType = valueTypeState.value ?: ValueType.None,
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
                numberDefaultUnit.value = (dv as? EventValue.NumberValue)?.unit ?: ""
            }
            effectiveType == ValueType.Exercise -> {
                val ev = dv as? EventValue.ExerciseValue
                exerciseDefaultSets.value = ev?.sets?.toString() ?: "3"
                exerciseDefaultReps.value = ev?.reps?.toString() ?: "15"
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
