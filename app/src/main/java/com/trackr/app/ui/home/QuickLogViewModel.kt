package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.EventValue
import com.trackr.app.ui.SaveResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.Instant

class QuickLogViewModel(
    private val repository: TrackrRepository,
    private val imageStore: ImageStore,
    preSelectedCategory: Category? = null,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    val categories: StateFlow<List<Category>> get() = TODO()

    val selectedCategory = MutableStateFlow<Category?>(preSelectedCategory)
    val timestamp = MutableStateFlow<Instant>(Instant.now(clock))
    val notes = MutableStateFlow("")
    val imagePath = MutableStateFlow<String?>(null)
    val value = MutableStateFlow<EventValue?>(null)

    private val _saveResult = MutableStateFlow<SaveResult>(SaveResult.Idle)
    val saveResult: StateFlow<SaveResult> = _saveResult.asStateFlow()

    fun selectCategory(category: Category) = TODO()
    suspend fun save() = TODO()
    fun reset() = TODO()
}
