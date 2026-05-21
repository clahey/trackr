package com.trackr.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.ConversionOutcome
import com.trackr.app.domain.Event
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.domain.convertOrDefault
import com.trackr.app.domain.matchesValueType
import com.trackr.app.ui.SaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

// @spec EL-UI-040, EL-UI-042, EL-UI-043, EL-UI-044, EL-NAV-005, EL-NAV-006, EL-PROC-002, APP-NAV-003
@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle["eventId"])

    val timestamp = MutableStateFlow<Instant>(Instant.EPOCH)
    val value = MutableStateFlow<EventValue?>(null)
    val notes = MutableStateFlow("")
    val imagePaths = MutableStateFlow<List<String>>(emptyList())

    private val _isValueEditable = MutableStateFlow(true)
    val isValueEditable: StateFlow<Boolean> = _isValueEditable.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private val _pendingDelete = MutableStateFlow(false)
    val pendingDelete: StateFlow<Boolean> = _pendingDelete.asStateFlow()

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    private val _deleteComplete = MutableStateFlow(false)
    val deleteComplete: StateFlow<Boolean> = _deleteComplete.asStateFlow()

    private val _category = MutableStateFlow<Category?>(null)
    val category: StateFlow<Category?> = _category.asStateFlow()

    // @spec EL-UI-062, EL-UI-063, EL-UI-064, EL-UI-065, EL-UI-067
    val conversionOutcome: StateFlow<ConversionOutcome?> = combine(value, _category) { v, cat ->
        if (cat == null || v == null || matchesValueType(v, cat.resolvedValueType)) null
        else convertOrDefault(v, cat.resolvedValueType)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var originalEvent: Event? = null
    private var originalImagePaths: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            val event = repository.getEventById(eventId).first()
            if (event == null) {
                _navigateBack.value = true
                return@launch
            }
            originalEvent = event
            originalImagePaths = event.imagePaths.toSet()
            timestamp.value = event.timestamp
            value.value = event.value
            notes.value = event.notes ?: ""
            imagePaths.value = event.imagePaths

            val loadedCategory = repository.getCategoryById(event.categoryId).first()
            _category.value = loadedCategory

            val isError = event.value is EventValue.ErrorValue
            _isValueEditable.value = if (isError) {
                false
            } else {
                loadedCategory?.resolvedValueType !is ValueType.Unknown
            }
        }
    }

    fun requestDelete() { _pendingDelete.value = true }
    fun cancelDelete() { _pendingDelete.value = false }

    suspend fun confirmDelete() {
        repository.deleteEvent(eventId)
        _deleteComplete.value = true
    }

    suspend fun save() {
        val event = originalEvent ?: return
        repository.saveEvent(
            event.copy(
                timestamp = timestamp.value,
                value = value.value,
                notes = notes.value.takeIf { it.isNotBlank() },
                imagePaths = imagePaths.value,
            )
        )
        _saveResult.value = SaveResult.Success
    }

    // @spec EL-UI-066
    fun applyConversion() {
        when (val outcome = conversionOutcome.value) {
            is ConversionOutcome.Converted -> value.value = outcome.value
            is ConversionOutcome.UsedDefault -> value.value = outcome.value
            ConversionOutcome.Discard -> value.value = null
            null -> Unit
        }
        _isValueEditable.value = true
    }

    fun addImage(path: String) {
        imagePaths.value = imagePaths.value + path
    }

    fun removeImage(path: String) {
        imagePaths.value = imagePaths.value - path
    }

    fun cancel() {
        val newImages = imagePaths.value.filter { it !in originalImagePaths }
        newImages.forEach { imageStore.delete(it) }
    }
}
