package com.trackr.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.theme.categoryColorForIndex
import com.trackr.app.ui.theme.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DeleteConfirmation(
    val categoryId: String,
    val ownEventCount: Int,
    val subCategoryCount: Int = 0,
    val isMetaCategory: Boolean = false,
)

data class GroupPickerState(
    val categoryId: String,
    val eligibleParents: List<Category.MetaCategory>,
    val isMoveOperation: Boolean,
)

// @spec CAT-UI-001, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006,
// CAT-UI-051, CAT-UI-052, DM-PROC-020
@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val repository: TrackrRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingDeleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val pendingDeleteConfirmation: StateFlow<DeleteConfirmation?> = _pendingDeleteConfirmation.asStateFlow()

    private val _pendingGroupPicker = MutableStateFlow<GroupPickerState?>(null)
    val pendingGroupPicker: StateFlow<GroupPickerState?> = _pendingGroupPicker.asStateFlow()

    // @spec CAT-UI-004, CAT-UI-005
    fun deleteCategory(id: String) {
        viewModelScope.launch {
            val ownCount = repository.getEventCountForCategory(id).first()
            val subCount = repository.getSubCategoryCount(id).first()
            if (ownCount == 0 && subCount == 0) {
                repository.deleteCategory(id)
            } else {
                val isMeta = repository.getCategoryById(id).first() is Category.MetaCategory
                _pendingDeleteConfirmation.value = DeleteConfirmation(
                    id,
                    ownEventCount = ownCount,
                    subCategoryCount = subCount,
                    isMetaCategory = isMeta,
                )
            }
        }
    }

    // @spec CAT-UI-006
    fun confirmDelete() {
        val pending = _pendingDeleteConfirmation.value ?: return
        viewModelScope.launch {
            if (pending.isMetaCategory) {
                repository.deleteMetaCategoryAndPromoteSubcategories(pending.categoryId)
            } else {
                repository.deleteCategory(pending.categoryId)
            }
            _pendingDeleteConfirmation.value = null
        }
    }

    fun cancelDelete() {
        _pendingDeleteConfirmation.value = null
    }

    // @spec CAT-UI-003, CAT-UI-051
    fun startAddToGroup(categoryId: String) {
        viewModelScope.launch {
            val allMeta = repository.getCategories().first().filterIsInstance<Category.MetaCategory>()
            val eligible = allMeta.filter { it.id != categoryId }
            _pendingGroupPicker.value = GroupPickerState(categoryId, eligible, isMoveOperation = false)
        }
    }

    // @spec CAT-UI-003, CAT-UI-051
    fun startMoveToAnotherGroup(categoryId: String) {
        viewModelScope.launch {
            val cat = repository.getCategoryById(categoryId).first() as? Category.SubCategory ?: return@launch
            val currentParentId = cat.parent.id
            val allMeta = repository.getCategories().first().filterIsInstance<Category.MetaCategory>()
            val eligible = allMeta.filter { it.id != currentParentId }
            _pendingGroupPicker.value = GroupPickerState(categoryId, eligible, isMoveOperation = true)
        }
    }

    fun dismissGroupPicker() {
        _pendingGroupPicker.value = null
    }

    // @spec CAT-UI-052, DM-PROC-020
    fun reparentCategory(categoryId: String, newParentId: String) {
        viewModelScope.launch {
            reparentCategoryInternal(categoryId, newParentId)
            _pendingGroupPicker.value = null
        }
    }

    // @spec CAT-UI-051
    fun reparentWithNewGroup(categoryId: String, newGroupName: String) {
        viewModelScope.launch {
            val cat = repository.getCategoryById(categoryId).first() ?: return@launch
            val allCategories = repository.getCategories().first()
            val minSortOrder = allCategories.minOfOrNull { it.sortOrder } ?: 0
            val colorIndex = repository.getAndIncrementNextCategoryColorIndex(categoryColorPalette.size)
            val newGroup = Category.MetaCategory(
                id = UUID.randomUUID().toString(),
                name = newGroupName,
                emoji = cat.resolvedEmoji,
                color = categoryColorForIndex(colorIndex),
                valueType = ValueType.None,
                unit = null,
                allowEmptyText = true,
                sortOrder = minSortOrder - 1,
            )
            repository.saveCategory(newGroup)
            reparentCategoryInternal(categoryId, newGroup.id)
            _pendingGroupPicker.value = null
        }
    }

    // @spec CAT-UI-003
    fun removeFromGroup(categoryId: String) {
        viewModelScope.launch {
            val cat = repository.getCategoryById(categoryId).first() as? Category.SubCategory ?: return@launch
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
        }
    }

    // @spec DM-PROC-020
    private suspend fun reparentCategoryInternal(categoryId: String, newParentId: String) {
        val cat = repository.getCategoryById(categoryId).first() ?: return
        val newParent = repository.getCategoryById(newParentId).first() as? Category.MetaCategory ?: return
        val newCat = when (cat) {
            is Category.MetaCategory -> Category.SubCategory(
                id = cat.id, name = cat.name,
                emoji = cat.emoji, color = cat.color, valueType = cat.valueType,
                unit = cat.unit, allowEmptyText = cat.allowEmptyText, sortOrder = cat.sortOrder,
                parent = newParent,
            )
            is Category.SubCategory -> cat.copy(parent = newParent)
        }
        repository.saveCategory(newCat)
    }
}
