package com.trackr.app.ui.category

import androidx.lifecycle.ViewModel
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.trackr.app.ui.SaveResult

class CategoryEditViewModel(
    private val repository: TrackrRepository,
    private val categoryId: String? = null,
) : ViewModel() {
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

    suspend fun save() = TODO()
}
