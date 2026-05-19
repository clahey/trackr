package com.trackr.app.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.theme.categoryColorForIndex
import com.trackr.app.ui.theme.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class ValueTypeWarningTier { IrreversibleSafe, Partial, Unsafe }

// @spec CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-036, CAT-UI-037, CAT-UI-038, CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043,
// APP-NAV-004
@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String? = savedStateHandle["categoryId"]

    val isEditMode: Boolean get() = categoryId != null

    val name = MutableStateFlow("")
    val emoji = MutableStateFlow("")
    val color = MutableStateFlow(0xFFE53935L)
    val valueType = MutableStateFlow<ValueType>(ValueType.None)
    val unit = MutableStateFlow("")

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.asStateFlow()

    private val _valueTypeWarning = MutableStateFlow<ValueTypeWarningTier?>(null)
    val valueTypeWarning: StateFlow<ValueTypeWarningTier?> = _valueTypeWarning.asStateFlow()

    private val _originalValueType = MutableStateFlow<ValueType?>(null)

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                val cat = repository.getCategoryById(categoryId).first()
                if (cat == null) {
                    _navigateBack.value = true
                    return@launch
                }
                name.value = cat.name
                emoji.value = cat.emoji
                color.value = cat.color
                valueType.value = cat.valueType
                unit.value = cat.unit ?: ""
                _originalValueType.value = cat.valueType
            }
            viewModelScope.launch {
                combine(
                    valueType,
                    _originalValueType,
                    repository.getEventCountForCategory(categoryId),
                ) { type, orig, count ->
                    if (orig == null || type == orig || count == 0) null
                    else warningTierFor(orig, type)
                }.collect { _valueTypeWarning.value = it }
            }
        } else {
            // @spec CAT-UI-043
            viewModelScope.launch {
                color.value = categoryColorForIndex(
                    repository.getAndIncrementNextCategoryColorIndex(categoryColorPalette.size)
                )
            }
        }
    }

    suspend fun save() {
        val nameVal = name.value.trim()
        if (nameVal.isEmpty()) {
            _saveResult.value = SaveResult.ValidationError("name")
            return
        }
        val emojiVal = emoji.value
        if (emojiVal.isEmpty()) {
            _saveResult.value = SaveResult.ValidationError("emoji")
            return
        }
        if (emojiVal.graphemeClusterCount() != 1) {
            _saveResult.value = SaveResult.ValidationError("emoji")
            return
        }

        val originalType = _originalValueType.value
        val sortOrder = categoryId?.let { repository.getCategoryById(it).first()?.sortOrder }
            ?: (repository.getCategories().first().minOfOrNull { it.sortOrder }?.minus(1) ?: 0)

        val category = Category(
            id = categoryId ?: UUID.randomUUID().toString(),
            name = nameVal,
            emoji = emojiVal,
            color = color.value,
            valueType = valueType.value,
            unit = unit.value.takeIf { it.isNotBlank() },
            allowEmptyText = true,
            sortOrder = sortOrder,
        )
        if (categoryId != null && originalType != null && category.valueType != originalType) {
            repository.saveCategoryAndMigrateEvents(category, originalType)
        } else {
            repository.saveCategory(category)
        }

        _saveResult.value = SaveResult.Success
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
