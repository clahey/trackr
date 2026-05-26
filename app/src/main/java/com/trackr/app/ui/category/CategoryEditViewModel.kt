package com.trackr.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ValueTypeWarningTier { IrreversibleSafe, Partial, Unsafe }

// @spec CAT-UI-004, CAT-UI-005, CAT-UI-012, CAT-UI-013,
// CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// CAT-UI-054, CAT-NAV-005, DM-PROC-019, DM-PROC-021, APP-NAV-004
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String? = savedStateHandle["categoryId"]
    private val parentId: String? = savedStateHandle["parentId"]

    val isEditMode: Boolean get() = categoryId != null
    val editingCategoryId: String? get() = categoryId

    val name = MutableStateFlow("")

    // Nullable raw state — null means inherit from parent (SubCategory only).
    // For MetaCategory mode these are always non-null after init.
    // @spec CAT-UI-054
    val emojiState = MutableStateFlow<String?>(null)
    val colorState = MutableStateFlow<Long?>(null)
    val valueTypeState = MutableStateFlow<ValueType?>(null)

    val unit = MutableStateFlow("")

    private val _parentCategory = MutableStateFlow<Category.MetaCategory?>(null)
    val parentCategory: StateFlow<Category.MetaCategory?> = _parentCategory.asStateFlow()

    // Effective values resolve null state to the parent's value.
    val effectiveEmoji: StateFlow<String> = combine(emojiState, _parentCategory) { e, parent ->
        e ?: parent?.emoji ?: ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val effectiveColor: StateFlow<Long> = combine(colorState, _parentCategory) { c, parent ->
        c ?: parent?.color ?: DEFAULT_CATEGORY_COLOR
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_CATEGORY_COLOR)

    val effectiveValueType: StateFlow<ValueType> = combine(valueTypeState, _parentCategory) { v, parent ->
        v ?: parent?.valueType ?: ValueType.None
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ValueType.None)

    val isEmojiInherited: StateFlow<Boolean> = emojiState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isColorInherited: StateFlow<Boolean> = colorState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isValueTypeInherited: StateFlow<Boolean> = valueTypeState.map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

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
                    unit.value = cat.unit ?: ""
                    when (cat) {
                        is Category.MetaCategory -> {
                            emojiState.value = cat.emoji
                            colorState.value = cat.color
                            valueTypeState.value = cat.valueType
                        }
                        is Category.SubCategory -> {
                            emojiState.value = cat.emoji
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
                // SubCategory create mode: load parent, leave state null (inherit).
                // Do NOT advance color counter (CAT-UI-043).
                viewModelScope.launch {
                    val parent = repository.getCategoryById(parentId).first()
                    if (parent is Category.MetaCategory) {
                        _parentCategory.value = parent
                    } else {
                        _navigateBack.value = true
                    }
                }
            }

            else -> {
                // MetaCategory create mode.
                emojiState.value = ""
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

        val emojiVal = emojiState.value
        if (emojiVal != null) {
            if (emojiVal.isEmpty()) { _saveResult.value = SaveResult.ValidationError("emoji"); return }
            if (emojiVal.graphemeClusterCount() != 1) {
                _saveResult.value = SaveResult.ValidationError("emoji"); return
            }
        } else if (_parentCategory.value == null) {
            // MetaCategory must have an emoji; only SubCategories can inherit (null).
            _saveResult.value = SaveResult.ValidationError("emoji"); return
        }

        val parent = _parentCategory.value
        val sortOrder = categoryId?.let { repository.getCategoryById(it).first()?.sortOrder }
            ?: (repository.getCategories().first().minOfOrNull { it.sortOrder }?.minus(1) ?: 0)

        val category: Category = if (parent != null) {
            // @spec CAT-UI-041
            Category.SubCategory(
                id = categoryId ?: UUID.randomUUID().toString(),
                name = nameVal,
                emoji = emojiState.value,
                color = colorState.value,
                valueType = valueTypeState.value,
                unit = unit.value.takeIf { it.isNotBlank() },
                allowEmptyText = true,
                sortOrder = sortOrder,
                parent = parent,
            )
        } else {
            Category.MetaCategory(
                id = categoryId ?: UUID.randomUUID().toString(),
                name = nameVal,
                emoji = emojiState.value ?: "",
                color = colorState.value ?: DEFAULT_CATEGORY_COLOR,
                valueType = valueTypeState.value ?: ValueType.None,
                unit = unit.value.takeIf { it.isNotBlank() },
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

    // @spec DM-PROC-019, CAT-NAV-011
    fun removeFromGroup() {
        val id = categoryId ?: return
        viewModelScope.launch {
            val cat = repository.getCategoryById(id).first() as? Category.SubCategory ?: return@launch
            repository.saveCategory(
                Category.MetaCategory(
                    id = cat.id,
                    name = cat.name,
                    emoji = cat.resolvedEmoji,
                    color = cat.resolvedColor,
                    valueType = cat.resolvedValueType,
                    unit = cat.unit,
                    allowEmptyText = cat.allowEmptyText,
                    sortOrder = cat.sortOrder,
                )
            )
            _saveResult.value = SaveResult.Success
        }
    }

    // @spec CAT-UI-004, CAT-UI-005, CAT-NAV-005
    fun requestDelete() {
        val id = categoryId ?: return
        val isMeta = _parentCategory.value == null
        val confirmation = deletionConfirmationIfNeeded(id, ownEventCount.value, subCategoryCount.value, isMeta)
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
            if (pending.isMetaCategory) {
                repository.deleteMetaCategoryAndPromoteSubcategories(pending.categoryId)
            } else {
                repository.deleteCategory(pending.categoryId)
            }
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

    private fun String.graphemeClusterCount(): Int {
        val bi = java.text.BreakIterator.getCharacterInstance()
        bi.setText(this)
        var count = 0
        while (bi.next() != java.text.BreakIterator.DONE) count++
        return count
    }
}
