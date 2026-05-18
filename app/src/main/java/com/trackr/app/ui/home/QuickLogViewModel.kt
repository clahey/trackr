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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.util.UUID

// @spec EL-UI-013, EL-UI-030, EL-UI-032, EL-UI-034, EL-UI-052b, EL-UI-054, EL-UI-055b,
// EL-NAV-002, EL-PROC-001
class QuickLogViewModel(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    preSelectedCategory: Category? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    val selectedCategory = MutableStateFlow<Category?>(preSelectedCategory)
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
            }
        }
    }

    fun selectCategory(category: Category) {
        selectedCategory.value = category
    }

    suspend fun save() {
        val category = selectedCategory.value ?: return
        when (category.valueType) {
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
        notes.value = ""
        imagePath.value = null
        value.value = null
    }
}
