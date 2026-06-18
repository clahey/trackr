package net.clahey.trackr.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.clahey.trackr.data.ImageStore
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.ValueUIState
import net.clahey.trackr.ui.components.toEventValue
import net.clahey.trackr.ui.components.toValueUIState
import net.clahey.trackr.ui.components.validateValueForSave
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EventDisplayState(
    val event: Event,
    val category: Category?,
    val valueUIState: ValueUIState = event.value.toValueUIState(category?.resolvedValueType ?: ValueType.None),
)

// @spec EL-UI-040, EL-UI-042, EL-UI-043, EL-UI-044, EL-UI-062, EL-UI-067, EL-NAV-005, EL-NAV-006, EL-PROC-002, APP-NAV-003, EL-NAV-008, EL-NAV-009, EL-NAV-011, EL-NAV-012
@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _currentEventId = MutableStateFlow<String>(checkNotNull(savedStateHandle["eventId"]))
    private val filterCategoryId: String? = savedStateHandle["filterCategoryId"]

    private val _timestamp = MutableStateFlow<Instant>(Instant.EPOCH)
    val timestamp: StateFlow<Instant> = _timestamp.asStateFlow()

    private val _value = MutableStateFlow<ValueUIState>(ValueUIState.None)
    val value: StateFlow<ValueUIState> = _value.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _imagePaths = MutableStateFlow<List<String>>(emptyList())
    val imagePaths: StateFlow<List<String>> = _imagePaths.asStateFlow()

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

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _showDiscardDialog = MutableStateFlow(false)
    val showDiscardDialog: StateFlow<Boolean> = _showDiscardDialog.asStateFlow()

    private var originalEvent: Event? = null
    private var originalImagePaths: Set<String> = emptySet()

    // @spec EL-NAV-008, EL-NAV-011
    private val _events: StateFlow<List<Event>> = (
        if (filterCategoryId == null) repository.getEvents()
        else repository.getEventsByCategoryIdIncludingChildren(filterCategoryId)
    ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val eventIds: StateFlow<List<String>> = _events
        .map { events -> events.map { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentIndex: StateFlow<Int> = combine(_currentEventId, _events) { id, events ->
        maxOf(0, events.indexOfFirst { it.id == id })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // @spec EL-NAV-009
    val prevEventState: StateFlow<EventDisplayState?> = combine(_currentEventId, _events) { id, events ->
        val idx = maxOf(0, events.indexOfFirst { it.id == id })
        if (idx > 0) events[idx - 1] else null
    }.flatMapLatest { event ->
        if (event == null) flowOf(null)
        else repository.getCategoryById(event.categoryId).map { cat -> EventDisplayState(event, cat) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nextEventState: StateFlow<EventDisplayState?> = combine(_currentEventId, _events) { id, events ->
        val idx = maxOf(0, events.indexOfFirst { it.id == id })
        if (idx < events.size - 1) events[idx + 1] else null
    }.flatMapLatest { event ->
        if (event == null) flowOf(null)
        else repository.getCategoryById(event.categoryId).map { cat -> EventDisplayState(event, cat) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val event = repository.getEventById(_currentEventId.value).first()
            if (event == null) {
                _navigateBack.value = true
                return@launch
            }
            loadEventData(event)
        }
    }

    private fun restoreFormFields(event: Event) {
        _timestamp.value = event.timestamp
        _notes.value = event.notes ?: ""
        _imagePaths.value = event.imagePaths
        _value.value = event.value.toValueUIState(_category.value?.resolvedValueType ?: ValueType.None)
    }

    private suspend fun loadEventData(event: Event) {
        originalEvent = event
        originalImagePaths = event.imagePaths.toSet()
        val cat = repository.getCategoryById(event.categoryId).first()
        _category.value = cat
        restoreFormFields(event)
    }

    fun requestDelete() { _pendingDelete.value = true }
    fun cancelDelete() { _pendingDelete.value = false }

    suspend fun confirmDelete() {
        val paths = originalEvent?.imagePaths ?: emptyList()
        repository.deleteEvent(_currentEventId.value)
        repository.deleteEventFiles(paths)
        _deleteComplete.value = true
    }

    suspend fun save() {
        if (performSave()) _saveResult.value = SaveResult.Success
    }

    // Save without triggering navigate-back; used when saving from the swipe dialog.
    suspend fun saveInPlace() {
        performSave()
        _showDiscardDialog.value = false
    }

    private suspend fun performSave(): Boolean {
        val event = originalEvent ?: return false
        val cat = _category.value
        if (cat != null) {
            val invalidField = validateValueForSave(_value.value, cat)
            if (invalidField != null) {
                _saveResult.value = SaveResult.ValidationError(invalidField)
                return false
            }
        }
        val saved = event.copy(
            timestamp = _timestamp.value,
            value = _value.value.toEventValue(),
            notes = _notes.value.takeIf { it.isNotBlank() },
            imagePaths = _imagePaths.value,
        )
        repository.saveEvent(saved)
        originalEvent = saved
        originalImagePaths = imagePaths.value.toSet()
        _isDirty.value = false
        return true
    }

    // Revert form to original event without navigating.
    fun discardInPlace() {
        val event = originalEvent ?: return
        val newImages = _imagePaths.value.filter { it !in originalImagePaths }
        newImages.forEach { imageStore.delete(it) }
        restoreFormFields(event)
        _isDirty.value = false
        _showDiscardDialog.value = false
    }

    fun scrollEnded() {
        if (_isDirty.value) _showDiscardDialog.value = true
    }

    fun pageSettled(page: Int) {
        if (!_isDirty.value) {
            val delta = page - currentIndex.value
            if (delta != 0) navigateToAdjacent(delta)
        }
    }

    fun dismissDiscardDialog() { _showDiscardDialog.value = false }

    fun setValue(state: ValueUIState) {
        _value.value = state
        _isDirty.value = true
    }

    fun setNotes(notes: String) {
        _notes.value = notes
        _isDirty.value = true
    }

    // @spec EL-UI-044a
    fun createImageFile(): String = imageStore.newFile().absolutePath

    fun cancelImage(path: String) {
        imageStore.delete(path)
    }

    fun addImage(path: String) {
        _imagePaths.value = _imagePaths.value + path
        _isDirty.value = true
    }

    fun removeImage(path: String) {
        _imagePaths.value = _imagePaths.value - path
        _isDirty.value = true
    }

    // @spec EL-NAV-007
    fun cancel() {
        val newImages = _imagePaths.value.filter { it !in originalImagePaths }
        newImages.forEach { imageStore.delete(it) }
    }

    // @spec EL-NAV-009
    fun navigateToAdjacent(delta: Int) {
        viewModelScope.launch { doNavigateToAdjacent(delta) }
    }

    private suspend fun doNavigateToAdjacent(delta: Int) {
        val events = _events.value
        val newIndex = currentIndex.value + delta
        if (newIndex < 0 || newIndex >= events.size) return
        val event = events[newIndex]
        loadEventData(event)
        _currentEventId.value = event.id
        _isDirty.value = false
    }
}
