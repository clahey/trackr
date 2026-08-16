package net.clahey.trackr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import net.clahey.trackr.ui.getStarterCategoryInputs
import androidx.compose.ui.unit.dp
import net.clahey.trackr.R
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.EventRow
import net.clahey.trackr.ui.components.formatValue
import net.clahey.trackr.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// @spec EL-UI-001, EL-UI-010, EL-UI-011, EL-UI-012, EL-UI-013, EL-UI-013b, EL-UI-014,
// EL-UI-017, EL-UI-018, EL-UI-019, EL-UI-019b, EL-UI-020, EL-UI-021, EL-UI-022,
// EL-UI-023, EL-UI-023b, EL-UI-030, EL-UI-032, EL-UI-034, EL-UI-045, EL-UI-070,
// EL-UI-071, EL-UI-072, EL-UI-073, EL-UI-074, EL-UI-075, EL-NAV-002, EL-PROC-001, APP-NAV-002
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEventEdit: (eventId: String, filterCategoryId: String?) -> Unit,
    onNavigateToCreateCategory: () -> Unit = {},
    onNavigateToCreateSubCategory: (parentId: String) -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    pendingSnackbarMessage: StateFlow<String?> = MutableStateFlow(null),
    onSnackbarMessageConsumed: () -> Unit = {},
    pendingCreatedCategoryId: StateFlow<String?> = MutableStateFlow(null),
    onCreatedCategoryConsumed: () -> Unit = {},
    homeVm: HomeViewModel = hiltViewModel(),
    quickLogVm: QuickLogViewModel = hiltViewModel(),
) {
    val dayGroups by homeVm.dayGroups.collectAsState()
    val emptyState by homeVm.emptyState.collectAsState()
    val activeFilter by homeVm.activeFilter.collectAsState()
    val pendingDelete by homeVm.pendingDelete.collectAsState()
    val preFilterTopDay by homeVm.preFilterTopDay.collectAsState()
    val scrollTarget by homeVm.scrollTarget.collectAsState()
    val categories by quickLogVm.categories.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isProgrammaticScroll by remember { mutableStateOf(false) }

    val snackbarMessage by pendingSnackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onSnackbarMessageConsumed()
    }

    // @spec EL-NAV-021
    // Reopen the quick-log sheet after returning from an inline category-create excursion.
    // Read once per composition so it fires on return, not on the tap that starts the excursion.
    LaunchedEffect(Unit) {
        // Always clear the one-shot result on return so a plain (non-sheet) create from the welcome
        // screen can't leak a stale pre-selection into a later sheet create.
        val newId = pendingCreatedCategoryId.value
        if (newId != null) onCreatedCategoryConsumed()
        // Only the sheet's "+ New" tiles set this flag, so it alone means "you came from the sheet."
        if (!quickLogVm.consumePendingCategoryCreate()) return@LaunchedEffect
        showSheet = true
        if (newId != null) {
            // Created from a sheet tile: pre-select at step 2 (await the row appearing).
            val created = quickLogVm.categories.mapNotNull { list -> list.firstOrNull { it.id == newId } }.first()
            quickLogVm.selectCategory(created)
        }
        // else cancelled from a sheet tile: reopen at step 1 (drill-down context preserved).
    }

    // Scroll to pre-filter day when filter applied
    LaunchedEffect(preFilterTopDay, dayGroups) {
        val targetDay = preFilterTopDay ?: return@LaunchedEffect
        val idx = dayGroups.indexOfFirst { it.date <= targetDay }
        if (idx >= 0) {
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(idx)
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    // @spec EL-UI-077
    // Scroll to a newly saved event once it appears in dayGroups
    LaunchedEffect(scrollTarget, dayGroups) {
        val targetId = scrollTarget ?: return@LaunchedEffect
        val idx = flattenedIndexOfEvent(dayGroups, targetId) ?: return@LaunchedEffect
        isProgrammaticScroll = true
        try {
            listState.animateScrollToItem(idx)
        } finally {
            isProgrammaticScroll = false
        }
        homeVm.consumeScrollTarget()
    }

    // Detect user scroll and discard pre-filter position
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !isProgrammaticScroll) {
                homeVm.onUserScrolled()
            }
        }
    }

    // @spec EL-UI-081, EL-UI-082
    val pendingQuickLogTarget by homeVm.pendingQuickLogTarget.collectAsState()
    LaunchedEffect(pendingQuickLogTarget) {
        when (val target = pendingQuickLogTarget) {
            is QuickLogTarget.DrillDown -> quickLogVm.expandMetaCategory(target.meta.id)
            is QuickLogTarget.DirectEntry -> quickLogVm.selectCategory(target.category)
            null -> return@LaunchedEffect
        }
        showSheet = true
        homeVm.consumePendingQuickLogTarget()
    }

    // @spec EL-UI-083
    val quickLogCategoryNotFound by homeVm.quickLogCategoryNotFound.collectAsState()
    val categoryNotFoundMessage = stringResource(R.string.category_not_found)
    LaunchedEffect(quickLogCategoryNotFound) {
        if (quickLogCategoryNotFound) {
            snackbarHostState.showSnackbar(categoryNotFoundMessage)
            homeVm.consumeQuickLogCategoryNotFound()
        }
    }

    val eventDeletedMessage = stringResource(R.string.event_deleted_snackbar)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            val result = snackbarHostState.showSnackbar(
                message = eventDeletedMessage,
                actionLabel = undoLabel,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> homeVm.undoDelete()
                SnackbarResult.Dismissed -> homeVm.clearPendingDelete()
            }
        }
    }

    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                actions = {
                    // @spec APP-NAV-010
                    IconButton(onClick = onNavigateToAbout, modifier = Modifier.testTag("about_action")) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.cd_about))
                    }
                },
            )
        },
        floatingActionButton = {
            // @spec EL-NAV-001, EL-UI-013, EL-UI-075
            FloatingActionButton(modifier = Modifier.testTag("log_event_fab"), onClick = {
                when (val f = activeFilter) {
                    is ActiveFilter.Sub -> quickLogVm.selectCategory(f.sub)
                    is ActiveFilter.TopLevel -> {
                        val hasSubCats = categories.filterIsInstance<Category.SubCategory>()
                            .any { it.parent.id == f.category.id }
                        if (hasSubCats) quickLogVm.expandMetaCategory(f.category.id)
                        else quickLogVm.selectCategory(f.category)
                    }
                    is ActiveFilter.All -> {}
                }
                showSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_log_event))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // @spec EL-UI-010, EL-UI-012, EL-UI-014, EL-UI-070, EL-UI-071
            if (categories.isNotEmpty()) {
                val metaCategories = categories.filterIsInstance<Category.MetaCategory>()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            modifier = Modifier.testTag("filter_all"),
                            selected = activeFilter is ActiveFilter.All,
                            onClick = { homeVm.setFilter(ActiveFilter.All) },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                    }
                    metaCategories.forEach { meta ->
                        val isMetaSelected = when (val f = activeFilter) {
                            is ActiveFilter.TopLevel -> f.category.id == meta.id
                            else -> false
                        }
                        val showSubChips = when (val f = activeFilter) {
                            is ActiveFilter.TopLevel -> f.category.id == meta.id
                            is ActiveFilter.Sub -> f.parent.id == meta.id
                            else -> false
                        }
                        item(key = "meta-${meta.id}") {
                            CategoryFilterChip(
                                category = meta,
                                selected = isMetaSelected,
                                onClick = {
                                    val f = activeFilter
                                    when {
                                        f is ActiveFilter.TopLevel && f.category.id == meta.id ->
                                            homeVm.setFilter(ActiveFilter.All)
                                        else -> homeVm.setFilter(ActiveFilter.TopLevel(meta))
                                    }
                                },
                            )
                        }
                        if (showSubChips) {
                            val subCats = categories.filterIsInstance<Category.SubCategory>()
                                .filter { it.parent.id == meta.id }
                            items(subCats, key = { "sub-${it.id}" }) { sub ->
                                val isSubSelected = when (val f = activeFilter) {
                                    is ActiveFilter.Sub -> f.sub.id == sub.id
                                    else -> false
                                }
                                CategoryFilterChip(
                                    category = sub,
                                    selected = isSubSelected,
                                    onClick = {
                                        homeVm.setFilter(
                                            if (isSubSelected) ActiveFilter.TopLevel(meta)
                                            else ActiveFilter.Sub(meta, sub)
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Captured to a local val: emptyState is a collectAsState()-delegated property, which the
            // compiler won't smart-cast to non-null across the check below.
            val currentEmptyState = emptyState
            if (currentEmptyState != null) {
                // @spec EL-UI-092, EL-UI-093, EL-UI-094
                EmptyTimeline(
                    state = currentEmptyState,
                    // Seed the categories only; the timeline then lands on the "No events" state,
                    // which points the user at the FAB (rather than a picker appearing on its own).
                    onAddStarters = { homeVm.addStarterCategories(getStarterCategoryInputs(context)) },
                    // Plain trip to category creation — no sheet reopen and no auto-log on return.
                    onCreateCategory = onNavigateToCreateCategory,
                    onClearFilter = { homeVm.setFilter(ActiveFilter.All) },
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                dayGroups.forEach { group ->
                    item(key = "header-${group.date}") {
                        DayHeader(date = group.date)
                    }
                    items(group.events, key = { entry ->
                        when (entry) {
                            is DayEntry.Entry -> entry.event.id
                            is DayEntry.UndoPlaceholder -> "undo-${entry.event.id}"
                        }
                    }) { entry ->
                        when (entry) {
                            is DayEntry.Entry -> SwipeableEventRow(
                                event = entry.event,
                                category = entry.category,
                                hasMismatch = entry.hasMismatch,
                                onSwipeDelete = { homeVm.swipeDelete(entry.event) },
                                // @spec EL-NAV-004
                                onClick = {
                                    val filterId = when (val f = activeFilter) {
                                        is ActiveFilter.All -> null
                                        is ActiveFilter.TopLevel -> f.category.id
                                        is ActiveFilter.Sub -> f.sub.id
                                    }
                                    onNavigateToEventEdit(entry.event.id, filterId)
                                },
                            )
                            is DayEntry.UndoPlaceholder -> UndoPlaceholderRow(
                                event = entry.event,
                                onUndo = { homeVm.undoDelete() },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        val saveResult by quickLogVm.saveResult.collectAsState()

        LaunchedEffect(saveResult) {
            if (saveResult is SaveResult.Success) {
                // @spec EL-UI-077
                val savedId = quickLogVm.lastSavedEventId.value
                val savedCategory = quickLogVm.selectedCategory.value
                if (savedId != null && savedCategory != null) {
                    homeVm.onEventLogged(savedId, savedCategory)
                }
                sheetState.hide()
                showSheet = false
                quickLogVm.reset()
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                quickLogVm.reset()
                showSheet = false
            },
            sheetState = sheetState,
        ) {
            QuickLogSheet(
                categories = categories,
                viewModel = quickLogVm,
                onNavigateToCreateCategory = onNavigateToCreateCategory,
                onNavigateToCreateSubCategory = onNavigateToCreateSubCategory,
                onDismiss = {
                    scope.launch { sheetState.hide() }
                    quickLogVm.reset()
                    showSheet = false
                },
            )
        }
    }
}

// @spec EL-UI-077
private fun flattenedIndexOfEvent(dayGroups: List<DayGroup>, eventId: String): Int? {
    var index = 0
    for (group in dayGroups) {
        index++ // day header item
        val within = group.events.indexOfFirst { it is DayEntry.Entry && it.event.id == eventId }
        if (within >= 0) return index + within
        index += group.events.size
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEventRow(
    event: Event,
    category: net.clahey.trackr.domain.Category,
    hasMismatch: Boolean,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                true
            } else false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete_swipe), tint = Color.Red)
            }
        },
    ) {
        EventRow(event = event, category = category, hasMismatch = hasMismatch, onClick = onClick)
    }
}


// @spec EL-UI-023c
@Composable
private fun UndoPlaceholderRow(event: Event, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(R.string.event_deleted_snackbar),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo) { Text(stringResource(R.string.action_undo)) }
    }
}

// @spec EL-UI-092, EL-UI-093, EL-UI-094
@Composable
private fun EmptyTimeline(
    state: TimelineEmptyState,
    onAddStarters: () -> Unit,
    onCreateCategory: () -> Unit,
    onClearFilter: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is TimelineEmptyState.NoCategories -> {
                EmptyText(
                    title = stringResource(R.string.empty_no_categories_title),
                    body = stringResource(R.string.empty_no_categories_body),
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAddStarters) { Text(stringResource(R.string.action_add_starter_categories)) }
                TextButton(onClick = onCreateCategory) { Text(stringResource(R.string.empty_create_category)) }
            }
            is TimelineEmptyState.NoEvents -> EmptyText(
                title = stringResource(R.string.empty_no_events_title),
                body = stringResource(R.string.empty_no_events_body),
            )
            is TimelineEmptyState.NoFilterMatch -> {
                EmptyText(
                    title = stringResource(R.string.empty_no_match_title, filterLabel(state.filter)),
                    body = stringResource(R.string.empty_no_match_body),
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClearFilter) { Text(stringResource(R.string.action_clear_filter)) }
            }
        }
    }
}

@Composable
private fun EmptyText(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

private fun filterLabel(filter: ActiveFilter): String = when (filter) {
    is ActiveFilter.TopLevel -> "${filter.category.resolvedEmoji} ${filter.category.name}"
    is ActiveFilter.Sub -> "${filter.sub.resolvedEmoji} ${filter.sub.name}"
    is ActiveFilter.All -> ""
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.timeline_day_today)
        today.minusDays(1) -> stringResource(R.string.timeline_day_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// @spec EL-UI-015
@Composable
private fun CategoryFilterChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    val color = Color(category.resolvedColor)
    FilterChip(
        modifier = Modifier.testTag("filter_chip_${category.id}"),
        selected = selected,
        onClick = onClick,
        label = { Text("${category.resolvedEmoji} ${category.name}") },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color,
            selectedLabelColor = Color(foregroundColorForBackground(category.resolvedColor)),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = color,
            selectedBorderColor = color,
        ),
    )
}

