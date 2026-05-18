package com.trackr.app.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeleteConfirmation(val categoryId: String, val eventCount: Int)

// @spec CAT-UI-001, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006
@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val repository: TrackrRepository,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingDeleteConfirmation = MutableStateFlow<DeleteConfirmation?>(null)
    val pendingDeleteConfirmation: StateFlow<DeleteConfirmation?> = _pendingDeleteConfirmation.asStateFlow()

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            val count = repository.getEventCountForCategory(id).first()
            if (count == 0) {
                repository.deleteCategory(id)
            } else {
                _pendingDeleteConfirmation.value = DeleteConfirmation(id, count)
            }
        }
    }

    fun confirmDelete() {
        val pending = _pendingDeleteConfirmation.value ?: return
        viewModelScope.launch {
            repository.deleteCategory(pending.categoryId)
            _pendingDeleteConfirmation.value = null
        }
    }

    fun cancelDelete() {
        _pendingDeleteConfirmation.value = null
    }
}
