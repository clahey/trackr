package com.trackr.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.TrackrRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.matchesValueType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

sealed class DayEntry {
    // @spec EL-UI-061
    data class Entry(val event: Event, val category: Category?) : DayEntry() {
        val hasMismatch: Boolean = category != null && !matchesValueType(event.value, category.valueType)
    }
    data class UndoPlaceholder(val event: Event) : DayEntry()
}

// @spec EL-UI-001, EL-UI-011, EL-UI-012, EL-UI-013b, EL-UI-017, EL-UI-018,
// EL-UI-019, EL-UI-019b, EL-UI-020, EL-UI-021, EL-UI-022, EL-UI-023, EL-UI-023b
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: TrackrRepository) : ViewModel() {
    private val _activeFilter = MutableStateFlow<Category?>(null)
    val activeFilter: StateFlow<Category?> = _activeFilter.asStateFlow()

    private val _dayGroups = MutableStateFlow<List<DayGroup>>(emptyList())
    val dayGroups: StateFlow<List<DayGroup>> = _dayGroups.asStateFlow()

    private val _pendingDelete = MutableStateFlow<Event?>(null)
    val pendingDelete: StateFlow<Event?> = _pendingDelete.asStateFlow()

    private val _preFilterTopDay = MutableStateFlow<LocalDate?>(null)
    val preFilterTopDay: StateFlow<LocalDate?> = _preFilterTopDay.asStateFlow()

    private var deleteSnapshot: Set<String> = emptySet()
    private var currentEvents: List<Event> = emptyList()

    init {
        viewModelScope.launch {
            _activeFilter
                .flatMapLatest { filter ->
                    if (filter != null) repository.getEventsByCategory(filter.id)
                    else repository.getEvents()
                }
                .combine(repository.getCategories()) { events, categories -> events to categories }
                .combine(_pendingDelete) { (events, categories), pending ->
                    Triple(events, categories, pending)
                }
                .collect { (events, categories, pending) ->
                    currentEvents = events
                    if (pending != null) {
                        val newIds = events.map { it.id }.toSet() - deleteSnapshot
                        if (newIds.isNotEmpty()) {
                            _pendingDelete.value = null
                            return@collect
                        }
                    }
                    _dayGroups.value = buildDayGroups(events, pending, categories)
                }
        }

        viewModelScope.launch {
            repository.getCategories().collect { categories ->
                val categoryIds = categories.map { it.id }.toSet()
                val filter = _activeFilter.value
                if (filter != null && filter.id !in categoryIds) {
                    _activeFilter.value = null
                }
                val pending = _pendingDelete.value
                if (pending != null && pending.categoryId !in categoryIds) {
                    _pendingDelete.value = null
                }
            }
        }
    }

    fun setFilter(category: Category?) {
        val wasFiltered = _activeFilter.value != null
        if (!wasFiltered && category != null) {
            _preFilterTopDay.value = currentEvents.firstOrNull()
                ?.timestamp?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }
        if (category == null) {
            _preFilterTopDay.value = null
        }
        _activeFilter.value = category
    }

    fun onUserScrolled() {
        _preFilterTopDay.value = null
    }

    fun swipeDelete(event: Event) {
        deleteSnapshot = currentEvents.map { it.id }.toSet()
        _pendingDelete.value = null
        viewModelScope.launch {
            repository.deleteEvent(event.id)
            _pendingDelete.value = event
        }
    }

    fun undoDelete() {
        val event = _pendingDelete.value ?: return
        _pendingDelete.value = null
        viewModelScope.launch {
            repository.saveEvent(event)
        }
    }

    fun clearPendingDelete() {
        _pendingDelete.value = null
    }

    private fun buildDayGroups(events: List<Event>, pending: Event?, categories: List<Category>): List<DayGroup> {
        val categoryMap = categories.associateBy { it.id }
        val combined: List<Pair<Event, Boolean>> = buildList {
            events.forEach { add(it to false) }
            if (pending != null) add(pending to true)
        }.sortedWith(
            compareByDescending<Pair<Event, Boolean>> { it.first.timestamp }
                .thenByDescending { it.first.createdAt }
                .thenBy { it.first.id }
        )

        return combined
            .groupBy { it.first.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
            .entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                DayGroup(
                    date = date,
                    events = items.map { (event, isPlaceholder) ->
                        if (isPlaceholder) DayEntry.UndoPlaceholder(event)
                        else DayEntry.Entry(event, categoryMap[event.categoryId])
                    }
                )
            }
    }
}
