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

sealed class ActiveFilter {
    data object All : ActiveFilter()
    data class TopLevel(val category: Category.MetaCategory) : ActiveFilter()
    data class Sub(val parent: Category.MetaCategory, val sub: Category.SubCategory) : ActiveFilter()
}

data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

sealed class DayEntry {
    // @spec EL-UI-061
    data class Entry(val event: Event, val category: Category) : DayEntry() {
        val hasMismatch: Boolean = category != null && !matchesValueType(event.value, category.resolvedValueType)
    }
    data class UndoPlaceholder(val event: Event) : DayEntry()
}

// @spec EL-UI-001, EL-UI-011, EL-UI-012, EL-UI-013b, EL-UI-017, EL-UI-018,
// EL-UI-019, EL-UI-019b, EL-UI-020, EL-UI-021, EL-UI-022, EL-UI-023, EL-UI-023b
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(private val repository: TrackrRepository) : ViewModel() {
    private val _activeFilter = MutableStateFlow<ActiveFilter>(ActiveFilter.All)
    val activeFilter: StateFlow<ActiveFilter> = _activeFilter.asStateFlow()

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
            combine(
                _activeFilter.flatMapLatest { filter ->
                    when (filter) {
                        is ActiveFilter.All -> repository.getEvents()
                        is ActiveFilter.TopLevel -> repository.getEventsByCategoryIdIncludingChildren(filter.category.id)
                        is ActiveFilter.Sub -> repository.getEventsByCategoryIdIncludingChildren(filter.sub.id)
                    }
                },
                repository.getCategories(),
                _pendingDelete,
            ) { events, categories, pending ->
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
            repository.getCategories().collect { cats ->
                val catIds = cats.map { it.id }.toSet()
                when (val filter = _activeFilter.value) {
                    is ActiveFilter.TopLevel -> {
                        if (filter.category.id !in catIds) _activeFilter.value = ActiveFilter.All
                    }
                    is ActiveFilter.Sub -> {
                        val subExists = cats.any { it.id == filter.sub.id }
                        val parentExists = cats.any { it.id == filter.parent.id }
                        when {
                            !subExists -> _activeFilter.value = ActiveFilter.All
                            !parentExists -> {
                                val promoted = cats.filterIsInstance<Category.MetaCategory>()
                                    .find { it.id == filter.sub.id }
                                _activeFilter.value = if (promoted != null)
                                    ActiveFilter.TopLevel(promoted)
                                else
                                    ActiveFilter.All
                            }
                        }
                    }
                    is ActiveFilter.All -> {}
                }
                val pending = _pendingDelete.value
                if (pending != null && pending.categoryId !in catIds) {
                    _pendingDelete.value = null
                }
            }
        }
    }

    // @spec EL-UI-012, EL-UI-014, EL-UI-017, EL-UI-018, EL-UI-019b
    fun setFilter(filter: ActiveFilter) {
        val wasAll = _activeFilter.value is ActiveFilter.All
        if (wasAll && filter !is ActiveFilter.All) {
            _preFilterTopDay.value = currentEvents.firstOrNull()
                ?.timestamp?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }
        if (filter is ActiveFilter.All) _preFilterTopDay.value = null
        _activeFilter.value = filter
    }

    fun onUserScrolled() {
        _preFilterTopDay.value = null
    }

    fun swipeDelete(event: Event) {
        deleteSnapshot = currentEvents.map { it.id }.toSet()
        val prev = _pendingDelete.value
        _pendingDelete.value = null
        viewModelScope.launch {
            if (prev != null) repository.deleteEventFiles(prev.imagePaths)
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
        val prev = _pendingDelete.value
        _pendingDelete.value = null
        if (prev != null) {
            viewModelScope.launch { repository.deleteEventFiles(prev.imagePaths) }
        }
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
                    events = items.mapNotNull { (event, isPlaceholder) ->
                        if (isPlaceholder) DayEntry.UndoPlaceholder(event)
                        else categoryMap[event.categoryId]?.let { DayEntry.Entry(event, it) }
                    }
                )
            }
    }
}
