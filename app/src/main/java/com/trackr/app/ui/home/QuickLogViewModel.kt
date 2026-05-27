package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.components.ValueUIState
import com.trackr.app.ui.components.defaultValueUIStateForType
import com.trackr.app.ui.components.editableStateFor
import com.trackr.app.ui.components.matchesType
import com.trackr.app.ui.components.toEventValue
import com.trackr.app.ui.components.toValueUIState
import com.trackr.app.ui.components.validateValueForSave
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

// @spec EL-UI-013, EL-UI-030, EL-UI-031a, EL-UI-031b, EL-UI-032, EL-UI-034,
// EL-UI-051b, EL-UI-052b, EL-UI-054, EL-UI-055b, EL-UI-059b, EL-UI-068,
// EL-UI-073, EL-UI-074, EL-UI-075, EL-UI-076,
// EL-NAV-002, EL-PROC-001
@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    private val clock: Clock,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    val selectedCategory = MutableStateFlow<Category?>(null)
    val expandedMetaCategoryId = MutableStateFlow<String?>(null)
    val timestamp = MutableStateFlow<Instant>(Instant.now(clock))
    val notes = MutableStateFlow("")
    val imagePath = MutableStateFlow<String?>(null)
    val value = MutableStateFlow<ValueUIState>(ValueUIState.None)
    val valueDirty = MutableStateFlow(false)

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getCategories().collect { cats ->
                _categories.value = cats
                val selected = selectedCategory.value
                if (selected != null && cats.none { it.id == selected.id }) {
                    selectedCategory.value = null
                }
                // @spec EL-UI-076
                val expandedId = expandedMetaCategoryId.value
                if (expandedId != null && cats.filterIsInstance<Category.MetaCategory>().none { it.id == expandedId }) {
                    expandedMetaCategoryId.value = null
                }
            }
        }
    }

    fun updateValue(state: ValueUIState) {
        value.value = state
        valueDirty.value = true
    }

    // @spec EL-UI-068, EL-UI-068b, EL-UI-068c
    fun selectCategory(category: Category) {
        val targetType = category.resolvedValueType
        if (!valueDirty.value) {
            value.value = defaultValueUIStateForType(targetType, category.unit)
        } else {
            val current = value.value
            val effectiveState = when {
                current is ValueUIState.Mismatched && current.editableState != null -> current.editableState
                current is ValueUIState.Mismatched -> current.originalValue.toValueUIState()
                else -> current
            }
            value.value = when {
                effectiveState is ValueUIState.None -> defaultValueUIStateForType(targetType, category.unit)
                effectiveState.matchesType(targetType) -> effectiveState
                else -> {
                    val ev = effectiveState.toEventValue()
                    if (ev == null) defaultValueUIStateForType(targetType, category.unit)
                    else ValueUIState.Mismatched(
                        originalValue = ev,
                        targetType = targetType,
                        editableState = editableStateFor(ev, targetType),
                    )
                }
            }
        }
        selectedCategory.value = category
        expandedMetaCategoryId.value = null
    }

    fun expandMetaCategory(id: String?) {
        expandedMetaCategoryId.value = id
    }

    suspend fun save() {
        val category = selectedCategory.value ?: return
        val invalidField = validateValueForSave(value.value, category)
        if (invalidField != null) {
            _saveResult.value = SaveResult.ValidationError(invalidField)
            return
        }
        val eventValue = value.value.toEventValue()

        val event = Event(
            id = UUID.randomUUID().toString(),
            categoryId = category.id,
            timestamp = timestamp.value,
            value = eventValue,
            notes = notes.value.takeIf { it.isNotBlank() },
            imagePaths = listOfNotNull(imagePath.value),
            createdAt = Instant.now(clock),
        )
        repository.saveEvent(event)
        _saveResult.value = SaveResult.Success
    }

    // @spec EL-UI-031a, EL-UI-031b
    fun createImageFile(): String = imageStore.newFile().absolutePath

    fun commitImage(path: String) {
        val old = imagePath.value
        if (old != null && old != path) imageStore.delete(old)
        imagePath.value = path
    }

    fun cancelImage(path: String) {
        imageStore.delete(path)
    }

    fun removeImage() {
        val path = imagePath.value ?: return
        imageStore.delete(path)
        imagePath.value = null
    }

    // @spec EL-NAV-002b, EL-UI-032
    fun reset() {
        val path = imagePath.value
        if (path != null) imageStore.delete(path)
        selectedCategory.value = null
        expandedMetaCategoryId.value = null
        timestamp.value = Instant.now(clock)
        notes.value = ""
        imagePath.value = null
        value.value = ValueUIState.None
        valueDirty.value = false
        _saveResult.value = SaveResult.Idle
    }
}
