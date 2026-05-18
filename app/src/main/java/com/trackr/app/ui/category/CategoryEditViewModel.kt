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

// @spec CAT-UI-020, CAT-UI-021, CAT-UI-022, CAT-UI-030, CAT-UI-031,
// CAT-UI-040, CAT-UI-041, CAT-UI-042, CAT-UI-043, APP-NAV-004
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

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.asStateFlow()

    private val _showValueTypeWarning = MutableStateFlow(false)
    val showValueTypeWarning: StateFlow<Boolean> = _showValueTypeWarning.asStateFlow()

    private val _originalValueType = MutableStateFlow<ValueType?>(null)

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                repository.getCategoryById(categoryId).first()?.let { cat ->
                    name.value = cat.name
                    emoji.value = cat.emoji
                    color.value = cat.color
                    valueType.value = cat.valueType
                    unit.value = cat.unit ?: ""
                    _originalValueType.value = cat.valueType
                }
            }
            viewModelScope.launch {
                combine(
                    valueType,
                    _originalValueType,
                    repository.getEventCountForCategory(categoryId),
                ) { type, orig, count ->
                    orig != null && type != orig && count > 0
                }.collect { _showValueTypeWarning.value = it }
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

        val existing = categoryId?.let { repository.getCategoryById(it).first() }
        val sortOrder = existing?.sortOrder
            ?: (repository.getCategories().first().minOfOrNull { it.sortOrder }?.minus(1) ?: 0)
        val colorVal = existing?.color
            ?: categoryColorForIndex(repository.getAndIncrementNextCategoryColorIndex(categoryColorPalette.size))

        val category = Category(
            id = categoryId ?: UUID.randomUUID().toString(),
            name = nameVal,
            emoji = emojiVal,
            color = colorVal,
            valueType = valueType.value,
            unit = unit.value.takeIf { it.isNotBlank() },
            allowEmptyText = true,
            sortOrder = sortOrder,
        )
        repository.saveCategory(category)
        _saveResult.value = SaveResult.Success
    }

    private fun String.graphemeClusterCount(): Int {
        val bi = java.text.BreakIterator.getCharacterInstance()
        bi.setText(this)
        var count = 0
        while (bi.next() != java.text.BreakIterator.DONE) count++
        return count
    }
}
