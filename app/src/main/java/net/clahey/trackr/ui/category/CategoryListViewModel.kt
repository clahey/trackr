package net.clahey.trackr.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.CategoryHasChildrenException
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.domain.ValueTypeWarningTier
import net.clahey.trackr.domain.warningTierFor
import net.clahey.trackr.reminders.ReminderScheduler
import net.clahey.trackr.ui.components.DragMoveResult
import net.clahey.trackr.ui.theme.categoryColorForIndex
import net.clahey.trackr.ui.theme.categoryColorPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DeleteConfirmation(
    val categoryId: String,
    val ownEventCount: Int,
    val subCategoryCount: Int = 0,
)

// @spec CAT-UI-004, CAT-UI-005
// Returns null when the category qualifies for immediate silent deletion.
fun deletionConfirmationIfNeeded(
    categoryId: String,
    ownEventCount: Int,
    subCategoryCount: Int,
): DeleteConfirmation? {
    if (ownEventCount == 0 && subCategoryCount == 0) return null
    return DeleteConfirmation(categoryId, ownEventCount, subCategoryCount)
}

data class GroupPickerState(
    val categoryId: String,
    val eligibleParents: List<Category.MetaCategory>,
    val isMoveOperation: Boolean,
)

// @spec CAT-UI-081, CAT-UI-082
// orderedSiblingIds is null for a menu-driven reparent (Add to group / Move to another
// group), which doesn't reposition siblings — non-null for a drag move, which does.
// onSettled is the DragReorderList widget's completion callback for a drag-driven move
// (see docs/llds/drag-reorder-list.md § Settling); a no-op for the menu-driven path, which
// has no widget to settle.
data class PendingValueTypeConfirmation(
    val category: Category,
    val fromType: ValueType,
    val tier: ValueTypeWarningTier,
    val orderedSiblingIds: List<String>?,
    val onSettled: () -> Unit = {},
)

// @spec CAT-UI-001, CAT-UI-002, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006, CAT-UI-007,
// CAT-UI-051, CAT-UI-052, CAT-UI-080, CAT-UI-081, CAT-UI-082, CAT-UI-084, DM-PROC-020
@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // @spec REM-PERM-004
    @OptIn(ExperimentalCoroutinesApi::class)
    val hasEnabledReminder: StateFlow<Boolean> = categories
        .mapLatest { repository.getAllEnabledRemindersOnce().isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _pendingDeleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val pendingDeleteConfirmation: StateFlow<DeleteConfirmation?> = _pendingDeleteConfirmation.asStateFlow()

    private val _pendingGroupPicker = MutableStateFlow<GroupPickerState?>(null)
    val pendingGroupPicker: StateFlow<GroupPickerState?> = _pendingGroupPicker.asStateFlow()

    private val _pendingValueTypeConfirmation = MutableStateFlow<PendingValueTypeConfirmation?>(null)
    val pendingValueTypeConfirmation: StateFlow<PendingValueTypeConfirmation?> =
        _pendingValueTypeConfirmation.asStateFlow()

    // @spec CAT-UI-084
    // Name of the category whose reparent-to-nest was rejected because it concurrently
    // gained SubCategories. One-shot: the screen formats a snackbar from it and calls
    // consumeReparentRejection(). Kept as a bare name (not a resolved message) because the
    // ViewModel has no Context; the string resource is resolved in the composable.
    private val _reparentRejectedCategoryName = MutableStateFlow<String?>(null)
    val reparentRejectedCategoryName: StateFlow<String?> = _reparentRejectedCategoryName.asStateFlow()

    fun consumeReparentRejection() {
        _reparentRejectedCategoryName.value = null
    }

    // @spec CAT-UI-004, CAT-UI-005, CAT-UI-007
    fun deleteCategory(id: String) {
        viewModelScope.launch {
            val ownCount = repository.getEventCountForCategory(id).first()
            val subCount = repository.getSubCategoryCount(id).first()
            val confirmation = deletionConfirmationIfNeeded(id, ownCount, subCount)
            if (confirmation == null) {
                repository.deleteCategory(id)
                reminderScheduler.cancel(id)
            } else {
                _pendingDeleteConfirmation.value = confirmation
            }
        }
    }

    // @spec CAT-UI-006, CAT-UI-007
    fun confirmDelete() {
        val pending = _pendingDeleteConfirmation.value ?: return
        viewModelScope.launch {
            repository.deleteCategory(pending.categoryId)
            reminderScheduler.cancel(pending.categoryId)
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

    // @spec CAT-UI-002, CAT-UI-080, CAT-UI-082
    fun onDragMove(result: DragMoveResult, onSettled: () -> Unit) {
        viewModelScope.launch {
            val moved = repository.getCategoryById(result.movedId).first()
            if (moved == null) {
                onSettled()
                return@launch
            }
            val newParent = result.newParentId?.let {
                repository.getCategoryById(it).first() as? Category.MetaCategory
            }
            val effectiveTypeBefore = moved.resolvedValueType
            val reconstructed = reconstructForMove(moved, newParent)
            persistReparent(reconstructed, effectiveTypeBefore, result.orderedSiblingIds, onSettled)
        }
    }

    // @spec CAT-UI-002
    // Mirrors the variant-conversion pattern already used by reparentCategoryInternal /
    // removeFromGroup: preserve all current explicit field values as overrides, no resets
    // on a parent change.
    private fun reconstructForMove(moved: Category, newParent: Category.MetaCategory?): Category =
        if (newParent != null) {
            when (moved) {
                is Category.MetaCategory -> Category.SubCategory(
                    id = moved.id, name = moved.name,
                    emoji = moved.emoji, color = moved.color, valueType = moved.valueType,
                    defaultValue = moved.defaultValue, allowEmptyText = moved.allowEmptyText,
                    sortOrder = moved.sortOrder, parent = newParent,
                )
                is Category.SubCategory -> moved.copy(parent = newParent)
            }
        } else {
            when (moved) {
                is Category.SubCategory -> Category.MetaCategory(
                    id = moved.id, name = moved.name,
                    emoji = moved.resolvedEmoji, color = moved.resolvedColor,
                    valueType = moved.resolvedValueType, defaultValue = moved.defaultValue,
                    allowEmptyText = moved.allowEmptyText, sortOrder = moved.sortOrder,
                )
                is Category.MetaCategory -> moved
            }
        }

    // @spec CAT-UI-081
    private fun valueTypeChangeNeeded(category: Category, effectiveTypeBefore: ValueType): Boolean =
        category is Category.SubCategory && category.valueType == null &&
            category.resolvedValueType != effectiveTypeBefore

    // @spec CAT-UI-080, CAT-UI-081, CAT-UI-082
    // Shared by onDragMove and reparentCategoryInternal so all reparent entry points
    // (drag, "Add to group", "Move to another group") behave consistently. onSettled is
    // called exactly once down every path: immediately below when no confirmation is
    // needed, or later from confirmPendingValueTypeChange/cancelPendingValueTypeChange
    // when one is.
    private suspend fun persistReparent(
        category: Category,
        effectiveTypeBefore: ValueType,
        orderedSiblingIds: List<String>?,
        onSettled: () -> Unit = {},
    ) {
        if (!valueTypeChangeNeeded(category, effectiveTypeBefore)) {
            persistOrReject(category) {
                if (orderedSiblingIds != null) repository.moveCategory(category, orderedSiblingIds)
                else repository.saveCategory(category)
            }
            onSettled()
            return
        }
        val ownEventCount = repository.getEventCountForCategory(category.id).first()
        val tier = warningTierFor(effectiveTypeBefore, category.resolvedValueType)
        if (ownEventCount == 0 || tier == null) {
            persistOrReject(category) {
                if (orderedSiblingIds != null) {
                    repository.moveCategoryAndMigrateEvents(category, orderedSiblingIds, effectiveTypeBefore)
                } else {
                    repository.saveCategoryAndMigrateEvents(category, effectiveTypeBefore)
                }
            }
            onSettled()
        } else {
            _pendingValueTypeConfirmation.value =
                PendingValueTypeConfirmation(category, effectiveTypeBefore, tier, orderedSiblingIds, onSettled)
        }
    }

    // @spec CAT-UI-084
    // Runs a persist that the in-transaction childlessness guard (DM-DATA-028) may reject
    // when the category concurrently gained SubCategories since the drag/menu snapshot, so
    // nesting it would exceed the two-level cap. On rejection the transaction has already
    // rolled back — nothing is persisted and the category stays where it was; we record its
    // name so the screen can show a snackbar. The caller still fires its completion callback,
    // so a drag settles the row back to its origin (CAT-UI-082).
    private suspend fun persistOrReject(category: Category, persist: suspend () -> Unit) {
        try {
            persist()
        } catch (e: CategoryHasChildrenException) {
            _reparentRejectedCategoryName.value = category.name
        }
    }

    // @spec CAT-UI-081, CAT-UI-082
    fun confirmPendingValueTypeChange() {
        val pending = _pendingValueTypeConfirmation.value ?: return
        viewModelScope.launch {
            persistOrReject(pending.category) {
                if (pending.orderedSiblingIds != null) {
                    repository.moveCategoryAndMigrateEvents(pending.category, pending.orderedSiblingIds, pending.fromType)
                } else {
                    repository.saveCategoryAndMigrateEvents(pending.category, pending.fromType)
                }
            }
            _pendingValueTypeConfirmation.value = null
            pending.onSettled()
        }
    }

    // @spec CAT-UI-081, CAT-UI-082
    fun cancelPendingValueTypeChange() {
        val pending = _pendingValueTypeConfirmation.value
        _pendingValueTypeConfirmation.value = null
        pending?.onSettled()
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
                defaultValue = null,
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
                    defaultValue = cat.defaultValue,
                    allowEmptyText = cat.allowEmptyText,
                    sortOrder = cat.sortOrder,
                )
            )
        }
    }

    // @spec DM-PROC-020, CAT-UI-081
    private suspend fun reparentCategoryInternal(categoryId: String, newParentId: String) {
        val cat = repository.getCategoryById(categoryId).first() ?: return
        val newParent = repository.getCategoryById(newParentId).first() as? Category.MetaCategory ?: return
        val effectiveTypeBefore = cat.resolvedValueType
        val newCat = reconstructForMove(cat, newParent)
        persistReparent(newCat, effectiveTypeBefore, orderedSiblingIds = null)
    }
}
