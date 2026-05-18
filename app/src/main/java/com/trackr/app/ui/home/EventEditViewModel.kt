package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.EventValue
import com.trackr.app.ui.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

class EventEditViewModel(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    private val eventId: String,
) : ViewModel() {

    val timestamp = MutableStateFlow<Instant>(Instant.EPOCH)
    val value = MutableStateFlow<EventValue?>(null)
    val notes = MutableStateFlow("")
    val imagePaths = MutableStateFlow<List<String>>(emptyList())

    private val _isValueEditable = MutableStateFlow(true)
    val isValueEditable: StateFlow<Boolean> = _isValueEditable.asStateFlow()

    private val _pendingDelete = MutableStateFlow(false)
    val pendingDelete: StateFlow<Boolean> = _pendingDelete.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    private val _deleteComplete = MutableStateFlow(false)
    val deleteComplete: StateFlow<Boolean> = _deleteComplete.asStateFlow()

    fun requestDelete() = TODO()
    fun cancelDelete() = TODO()
    suspend fun confirmDelete() = TODO()
    suspend fun save() = TODO()
    fun addImage(path: String) = TODO()
    fun removeImage(path: String) = TODO()
    fun cancel() = TODO()
}
