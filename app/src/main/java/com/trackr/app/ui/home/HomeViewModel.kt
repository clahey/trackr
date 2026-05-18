package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

sealed class DayEntry {
    data class Entry(val event: Event) : DayEntry()
    data class UndoPlaceholder(val event: Event) : DayEntry()
}

class HomeViewModel(private val repository: TrackrRepository) : ViewModel() {
    private val _activeFilter = MutableStateFlow<Category?>(null)
    val activeFilter: StateFlow<Category?> = _activeFilter.asStateFlow()

    private val _dayGroups = MutableStateFlow<List<DayGroup>>(emptyList())
    val dayGroups: StateFlow<List<DayGroup>> = _dayGroups.asStateFlow()

    private val _pendingDelete = MutableStateFlow<Event?>(null)
    val pendingDelete: StateFlow<Event?> = _pendingDelete.asStateFlow()

    private val _preFilterTopDay = MutableStateFlow<LocalDate?>(null)
    val preFilterTopDay: StateFlow<LocalDate?> = _preFilterTopDay.asStateFlow()

    fun setFilter(category: Category?) = TODO()
    fun onUserScrolled() = TODO()
    fun swipeDelete(event: Event) = TODO()
    fun undoDelete() = TODO()
    fun clearPendingDelete() = TODO()
}
