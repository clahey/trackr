package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.EventValue
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

// @spec EL-UI-013, EL-UI-030, EL-UI-032, EL-UI-034, EL-UI-052b, EL-UI-054, EL-UI-055b,
// EL-UI-073, EL-UI-074, EL-UI-075, EL-UI-076, EL-NAV-002, EL-PROC-001
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
    val value = MutableStateFlow<EventValue?>(null)

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

    fun selectCategory(category: Category) {
        selectedCategory.value = category
        expandedMetaCategoryId.value = null
    }

    fun expandMetaCategory(id: String?) {
        expandedMetaCategoryId.value = id
    }

    suspend fun save() {
        val category = selectedCategory.value ?: return
        when (category.resolvedValueType) {
            ValueType.Number -> if (value.value == null) {
                _saveResult.value = SaveResult.ValidationError("value")
                return
            }
            ValueType.Duration -> if (value.value == null) {
                _saveResult.value = SaveResult.ValidationError("value")
                return
            }
            ValueType.Text -> {
                val v = value.value
                if (!category.allowEmptyText &&
                    (v == null || (v is EventValue.TextValue && v.text.isEmpty()))
                ) {
                    _saveResult.value = SaveResult.ValidationError("value")
                    return
                }
            }
            else -> {}
        }

        val event = Event(
            id = UUID.randomUUID().toString(),
            categoryId = category.id,
            timestamp = timestamp.value,
            value = value.value,
            notes = notes.value.takeIf { it.isNotBlank() },
            imagePaths = listOfNotNull(imagePath.value),
            createdAt = Instant.now(clock),
        )
        repository.saveEvent(event)
        _saveResult.value = SaveResult.Success
    }

    fun reset() {
        val path = imagePath.value
        if (path != null) imageStore.delete(path)
        selectedCategory.value = null
        expandedMetaCategoryId.value = null
        notes.value = ""
        imagePath.value = null
        value.value = null
        _saveResult.value = SaveResult.Idle
    }
}
