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
import net.clahey.trackr.domain.ValueTypeWarningTier
import net.clahey.trackr.domain.warningTierFor
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

enum class EmojiMode { INHERIT, CUSTOM }

class InvalidEmojiException(message: String) : Exception(message)

data class EmojiUIState(val mode: EmojiMode, val customValue: String) {
    // @spec CAT-UI-054, CAT-UI-062
    fun getPreviewEmoji(parent: Category.MetaCategory?): String =
        if (mode == EmojiMode.INHERIT) parent?.emoji ?: "" else customValue

    // @spec CAT-UI-054, CAT-UI-062
    fun getEmojiToSave(parent: Category.MetaCategory?): Result<String?> {
        if (parent != null && mode == EmojiMode.INHERIT) return Result.success(null)
        if (customValue.isEmpty()) return Result.failure(InvalidEmojiException("Emoji is required."))
        if (customValue.graphemeClusterCount() != 1) {
            return Result.failure(InvalidEmojiException("Emoji must be a single character."))
        }
        return Result.success(customValue)
    }
}

private fun String.graphemeClusterCount(): Int {
    val bi = java.text.BreakIterator.getCharacterInstance()
    bi.setText(this)
    var count = 0
    while (bi.next() != java.text.BreakIterator.DONE) count++
    return count
}

data class DefaultValueUIState(
    val numberDefaultUnit: String = "",
    val exerciseDefaultSets: String = "3",
    val exerciseDefaultReps: String = "15",
    val stored: EventValue? = null,
    val dirty: Boolean = false,
) {
    // @spec CAT-UI-059 — the value implied by the current live form state, regardless of dirty
    fun getLiveDefault(effectiveType: ValueType): EventValue? = when (effectiveType) {
        ValueType.Number -> EventValue.NumberValue(
            (stored as? EventValue.NumberValue)?.value ?: 0.0,
            numberDefaultUnit.takeIf { it.isNotBlank() },
        )
        ValueType.Exercise -> EventValue.ExerciseValue(
            exerciseDefaultSets.toIntOrNull() ?: 3,
            exerciseDefaultReps.toIntOrNull() ?: 15,
        )
        else -> stored?.takeIf { matchesValueType(it, effectiveType) }
    }

    // @spec CAT-UI-059 — what the live preview card shows: the live default when there is one,
    // else a plausible sample for types with no default-value editing UI at all. Never used for
    // save — getValueToSave calls getLiveDefault directly so a sample is never persisted.
    fun getPreviewValue(effectiveType: ValueType): EventValue? = getLiveDefault(effectiveType) ?: when (effectiveType) {
        ValueType.None -> null
        ValueType.Scale -> EventValue.Scale(7)
        ValueType.Boolean -> EventValue.BooleanValue(true)
        ValueType.Text -> EventValue.TextValue("Sample")
        ValueType.Duration -> EventValue.DurationValue(90.seconds)
        else -> null
    }

    // @spec CAT-UI-063, CAT-UI-064, CAT-UI-065, CAT-UI-066
    fun getValueToSave(effectiveType: ValueType): EventValue? = when {
        !dirty -> stored  // CAT-UI-066: preserve unchanged when user hasn't edited fields
        effectiveType == ValueType.Number || effectiveType == ValueType.Exercise -> getLiveDefault(effectiveType)
        else -> stored  // CAT-UI-065: leave unchanged
    }

    fun withUpdatedUnit(value: String) = copy(numberDefaultUnit = value, dirty = true)
    fun withUpdatedSets(value: String) = copy(exerciseDefaultSets = value, dirty = true)
    fun withUpdatedReps(value: String) = copy(exerciseDefaultReps = value, dirty = true)

    companion object {
        // @spec CAT-UI-011, CAT-UI-011a — loads an existing stored default: display fields seeded
        // from it, and it becomes the save-preservation baseline (CAT-UI-066).
        fun fromStored(dv: EventValue?, effectiveType: ValueType): DefaultValueUIState =
            seedDisplay(dv, effectiveType).copy(stored = dv)

        // @spec CAT-UI-066 — pre-populates display only from the parent's resolved default
        // (SubCategory create); no baseline to preserve, so an untouched save stays null (inherit).
        fun seedFromParentPreview(dv: EventValue?, effectiveType: ValueType): DefaultValueUIState =
            seedDisplay(dv, effectiveType)

        private fun seedDisplay(dv: EventValue?, effectiveType: ValueType): DefaultValueUIState = when (effectiveType) {
            ValueType.Number -> DefaultValueUIState(numberDefaultUnit = (dv as? EventValue.NumberValue)?.unit ?: "")
            ValueType.Exercise -> {
                val ev = dv as? EventValue.ExerciseValue
                DefaultValueUIState(
                    exerciseDefaultSets = ev?.sets?.toString() ?: "3",
                    exerciseDefaultReps = ev?.reps?.toString() ?: "15",
                )
            }
            else -> DefaultValueUIState()
        }
    }
}

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

    // @spec CAT-UI-067 — create mode is "dirty" from the start so the Save button shows immediately
    private val _isDirty = MutableStateFlow(categoryId == null)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    // @spec CAT-NAV-006 — tracks whether the user has actually edited a field; drives the
    // unsaved-changes back-guard, so an untouched new-category screen doesn't warn on immediate back.
    private val _hasUserEdits = MutableStateFlow(false)
    val hasUserEdits: StateFlow<Boolean> = _hasUserEdits.asStateFlow()

    private fun markEdited() {
        _isDirty.value = true
        _hasUserEdits.value = true
    }

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    fun setName(value: String) { _name.value = value; markEdited() }

    // @spec CAT-UI-054
    private val _emojiUIState = MutableStateFlow(EmojiUIState(EmojiMode.INHERIT, ""))
    val emojiUIState: StateFlow<EmojiUIState> = _emojiUIState.asStateFlow()
    fun setEmojiUIState(value: EmojiUIState) { _emojiUIState.value = value; markEdited() }

    private val _colorState = MutableStateFlow<Long?>(null)
    val colorState: StateFlow<Long?> = _colorState.asStateFlow()
    fun setColorState(value: Long?) { _colorState.value = value; markEdited() }

    private val _valueTypeState = MutableStateFlow<ValueType?>(null)
    val valueTypeState: StateFlow<ValueType?> = _valueTypeState.asStateFlow()
    fun setValueTypeState(value: ValueType?) { _valueTypeState.value = value; markEdited() }

    private val _defaultValueUIState = MutableStateFlow(DefaultValueUIState())
    val numberDefaultUnit: StateFlow<String> = _defaultValueUIState.map { it.numberDefaultUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultValueUIState().numberDefaultUnit)
    val exerciseDefaultSets: StateFlow<String> = _defaultValueUIState.map { it.exerciseDefaultSets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultValueUIState().exerciseDefaultSets)
    val exerciseDefaultReps: StateFlow<String> = _defaultValueUIState.map { it.exerciseDefaultReps }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultValueUIState().exerciseDefaultReps)

    fun updateNumberDefaultUnit(value: String) {
        _defaultValueUIState.value = _defaultValueUIState.value.withUpdatedUnit(value)
        markEdited()
    }
    fun updateExerciseDefaultSets(value: String) {
        _defaultValueUIState.value = _defaultValueUIState.value.withUpdatedSets(value)
        markEdited()
    }
    fun updateExerciseDefaultReps(value: String) {
        _defaultValueUIState.value = _defaultValueUIState.value.withUpdatedReps(value)
        markEdited()
    }

    private val _parentCategory = MutableStateFlow<Category.MetaCategory?>(null)
    val parentCategory: StateFlow<Category.MetaCategory?> = _parentCategory.asStateFlow()

    val effectiveEmoji: StateFlow<String> = combine(_emojiUIState, _parentCategory) { state, parent ->
        state.getPreviewEmoji(parent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val effectiveColor: StateFlow<Long> = combine(_colorState, _parentCategory) { c, parent ->
        c ?: parent?.color ?: DEFAULT_CATEGORY_COLOR
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CATEGORY_COLOR)

    val effectiveValueType: StateFlow<ValueType> = combine(_valueTypeState, _parentCategory) { v, parent ->
        v ?: parent?.valueType ?: ValueType.None
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ValueType.None)

    // @spec CAT-UI-059
    val previewEventValue: StateFlow<EventValue?> = combine(
        effectiveValueType, _defaultValueUIState,
    ) { vt, dvState ->
        dvState.getPreviewValue(vt)
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
                    _defaultValueUIState.value = DefaultValueUIState.fromStored(cat.defaultValue, cat.resolvedValueType)
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
                        _defaultValueUIState.value =
                            DefaultValueUIState.seedFromParentPreview(parent.resolvedDefaultValue, parent.resolvedValueType)
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
        val emojiToSave = _emojiUIState.value.getEmojiToSave(parent).getOrElse {
            _saveResult.value = SaveResult.ValidationError("emoji"); return
        }
        val sortOrder = categoryId?.let { repository.getCategoryById(it).first()?.sortOrder }
            ?: (repository.getCategories().first().minOfOrNull { it.sortOrder }?.minus(1) ?: 0)

        val defaultValueToSave = _defaultValueUIState.value.getValueToSave(effectiveValueType.value)

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
        // @spec CAT-NAV-020
        _savedCategoryId.value = category.id
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
}
