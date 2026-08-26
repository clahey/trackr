package net.clahey.trackr.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.clahey.trackr.data.ReminderNotifier
import net.clahey.trackr.data.TrackrRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.domain.matchesValueType
import net.clahey.trackr.domain.outstandingReminders as computeOutstanding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed class ActiveFilter {
    data object All : ActiveFilter()
    data class TopLevel(val category: Category.MetaCategory) : ActiveFilter()
    data class Sub(val parent: Category.MetaCategory, val sub: Category.SubCategory) : ActiveFilter()
}

// @spec EL-UI-081
sealed class QuickLogTarget {
    data class DrillDown(val meta: Category.MetaCategory) : QuickLogTarget()
    data class DirectEntry(val category: Category) : QuickLogTarget()
}

data class DayGroup(val date: LocalDate, val events: List<DayEntry>)

/** A reminder that fired and hasn't been dealt with, resolved for display. */
data class OutstandingReminderRow(
    val categoryId: String,
    val emoji: String,
    val name: String,
    val postedAt: Instant,
)

// @spec EL-UI-092, EL-UI-093, EL-UI-094
sealed class TimelineEmptyState {
    data object NoCategories : TimelineEmptyState()
    data object NoEvents : TimelineEmptyState()
    data class NoFilterMatch(val filter: ActiveFilter) : TimelineEmptyState()
}

sealed class DayEntry {
    // @spec EL-UI-061
    data class Entry(val event: Event, val category: Category) : DayEntry() {
        val hasMismatch: Boolean = category != null && !matchesValueType(event.value, category.resolvedValueType)
    }
    data class UndoPlaceholder(val event: Event) : DayEntry()
}

// @spec EL-UI-001, EL-UI-011, EL-UI-012, EL-UI-013b, EL-UI-017, EL-UI-018,
// EL-UI-019, EL-UI-019b, EL-UI-020, EL-UI-021, EL-UI-022, EL-UI-023, EL-UI-023b,
// EL-UI-080, EL-UI-081, EL-UI-082, EL-UI-083
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TrackrRepository,
    private val notifier: ReminderNotifier,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _activeFilter = MutableStateFlow<ActiveFilter>(ActiveFilter.All)
    val activeFilter: StateFlow<ActiveFilter> = _activeFilter.asStateFlow()

    private val _pendingQuickLogTarget = MutableStateFlow<QuickLogTarget?>(null)
    val pendingQuickLogTarget: StateFlow<QuickLogTarget?> = _pendingQuickLogTarget.asStateFlow()

    private val _quickLogCategoryNotFound = MutableStateFlow(false)
    val quickLogCategoryNotFound: StateFlow<Boolean> = _quickLogCategoryNotFound.asStateFlow()

    // Observed, not polled: the notifier updates its own state on every change, so this screen
    // never decides when to look (REM-NOTIF-009).
    // @spec EL-UI-096
    val outstandingReminders: StateFlow<List<OutstandingReminderRow>> =
        combine(notifier.outstanding, repository.getCategories()) { outstanding, categories ->
            outstanding.mapNotNull { reminder ->
                val category = categories.find { it.id == reminder.categoryId }
                    ?: return@mapNotNull null
                OutstandingReminderRow(
                    categoryId = reminder.categoryId,
                    emoji = category.resolvedEmoji,
                    name = category.name,
                    postedAt = reminder.postedAt,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dayGroups = MutableStateFlow<List<DayGroup>>(emptyList())
    val dayGroups: StateFlow<List<DayGroup>> = _dayGroups.asStateFlow()

    // @spec EL-UI-092, EL-UI-093, EL-UI-094 — null while the timeline has content
    private val _emptyState = MutableStateFlow<TimelineEmptyState?>(null)
    val emptyState: StateFlow<TimelineEmptyState?> = _emptyState.asStateFlow()

    private val _pendingDelete = MutableStateFlow<Event?>(null)
    val pendingDelete: StateFlow<Event?> = _pendingDelete.asStateFlow()

    private val _preFilterTopDay = MutableStateFlow<LocalDate?>(null)
    val preFilterTopDay: StateFlow<LocalDate?> = _preFilterTopDay.asStateFlow()

    private val _scrollTarget = MutableStateFlow<String?>(null)
    val scrollTarget: StateFlow<String?> = _scrollTarget.asStateFlow()

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
                val groups = buildDayGroups(events, pending, categories)
                _dayGroups.value = groups
                _emptyState.value = computeEmptyState(groups, categories, _activeFilter.value)
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

        // @spec EL-UI-080, EL-UI-081, EL-UI-082, EL-UI-083
        // Removed as it is read: the handle is saved and restored with its back stack entry, so an
        // argument left in place is handed to a fresh HomeViewModel after the task is restored.
        val quickLogCategoryId: String? = savedStateHandle.remove("quickLogCategoryId")
        if (quickLogCategoryId != null) {
            viewModelScope.launch { openQuickLogFor(quickLogCategoryId) }
        }

    }

    /**
     * Opens the quick-log sheet at [categoryId]'s target, or reports the category missing.
     *
     * Shared by the notification deep link and by a tap on an outstanding-reminder row: both are
     * the same reminder, so they resolve the same target and carry the same filter side effect
     * (EL-UI-098).
     */
    // @spec EL-UI-081, EL-UI-082, EL-UI-083
    private suspend fun openQuickLogFor(categoryId: String) {
        val categories = repository.getCategories().first()
        val category = categories.find { it.id == categoryId }
        if (category == null) {
            _quickLogCategoryNotFound.value = true
            return
        }
        val hasSubCategories = category is Category.MetaCategory &&
            categories.any { it is Category.SubCategory && it.parent.id == category.id }
        if (category is Category.MetaCategory && hasSubCategories) {
            setFilter(ActiveFilter.TopLevel(category))
            _pendingQuickLogTarget.value = QuickLogTarget.DrillDown(category)
        } else {
            _pendingQuickLogTarget.value = QuickLogTarget.DirectEntry(category)
        }
    }

    // @spec EL-UI-098
    fun onOutstandingReminderClick(categoryId: String) {
        notifier.cancelReminderNotification(categoryId)
        viewModelScope.launch { openQuickLogFor(categoryId) }
    }

    // The in-app equivalent of swiping the notification away: clears it, logs nothing.
    // @spec EL-UI-099
    fun onOutstandingReminderDismiss(categoryId: String) {
        notifier.cancelReminderNotification(categoryId)
    }

    fun consumePendingQuickLogTarget() {
        _pendingQuickLogTarget.value = null
    }

    fun consumeQuickLogCategoryNotFound() {
        _quickLogCategoryNotFound.value = false
    }

    // @spec EL-UI-012, EL-UI-014, EL-UI-017, EL-UI-018, EL-UI-019b
    fun setFilter(filter: ActiveFilter) {
        val wasAll = _activeFilter.value is ActiveFilter.All
        if (wasAll && filter !is ActiveFilter.All) {
            _preFilterTopDay.value = currentEvents.firstOrNull()
                ?.timestamp?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }
        if (filter is ActiveFilter.All) _preFilterTopDay.value = null
        // @spec EL-UI-077b
        if (filter != _activeFilter.value) _scrollTarget.value = null
        _activeFilter.value = filter
    }

    fun onUserScrolled() {
        _preFilterTopDay.value = null
    }

    // @spec EL-UI-092, EL-UI-093, EL-UI-094
    private fun computeEmptyState(
        groups: List<DayGroup>,
        categories: List<Category>,
        filter: ActiveFilter,
    ): TimelineEmptyState? = when {
        groups.isNotEmpty() -> null
        categories.isEmpty() -> TimelineEmptyState.NoCategories
        filter !is ActiveFilter.All -> TimelineEmptyState.NoFilterMatch(filter)
        else -> TimelineEmptyState.NoEvents
    }

    // @spec CAT-UI-090
    fun addStarterCategories(specs: List<net.clahey.trackr.domain.StarterCategoryInput>) {
        viewModelScope.launch { repository.addStarterCategories(specs) }
    }

    // @spec EL-UI-077, EL-UI-077a, EL-UI-077c
    fun onEventLogged(eventId: String, category: Category) {
        if (!categoryMatchesFilter(category, _activeFilter.value)) return
        _preFilterTopDay.value = null
        _scrollTarget.value = eventId
    }

    fun consumeScrollTarget() {
        _scrollTarget.value = null
    }

    private fun categoryMatchesFilter(category: Category, filter: ActiveFilter): Boolean = when (filter) {
        is ActiveFilter.All -> true
        is ActiveFilter.TopLevel ->
            category.id == filter.category.id ||
                (category is Category.SubCategory && category.parent.id == filter.category.id)
        is ActiveFilter.Sub -> category.id == filter.sub.id
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
