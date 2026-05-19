package com.trackr.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.components.ValueInputField
import com.trackr.app.ui.components.formatValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// @spec EL-UI-001, EL-UI-011, EL-UI-012, EL-UI-013, EL-UI-013b, EL-UI-017, EL-UI-018,
// EL-UI-019, EL-UI-019b, EL-UI-020, EL-UI-021, EL-UI-022, EL-UI-023, EL-UI-023b,
// EL-UI-030, EL-UI-032, EL-UI-034, EL-UI-045, EL-NAV-002, EL-PROC-001, APP-NAV-002
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToEventEdit: (String) -> Unit,
    pendingSnackbarMessage: StateFlow<String?> = MutableStateFlow(null),
    onSnackbarMessageConsumed: () -> Unit = {},
    homeVm: HomeViewModel = hiltViewModel(),
    quickLogVm: QuickLogViewModel = hiltViewModel(),
) {
    val dayGroups by homeVm.dayGroups.collectAsState()
    val activeFilter by homeVm.activeFilter.collectAsState()
    val pendingDelete by homeVm.pendingDelete.collectAsState()
    val preFilterTopDay by homeVm.preFilterTopDay.collectAsState()
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

    // Scroll to pre-filter day when filter applied
    LaunchedEffect(preFilterTopDay, dayGroups) {
        val targetDay = preFilterTopDay ?: return@LaunchedEffect
        val idx = dayGroups.indexOfFirst { it.date <= targetDay }
        if (idx >= 0) {
            isProgrammaticScroll = true
            listState.animateScrollToItem(idx)
            isProgrammaticScroll = false
        }
    }

    // Detect user scroll and discard pre-filter position
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && !isProgrammaticScroll) {
                homeVm.onUserScrolled()
            }
        }
    }

    LaunchedEffect(pendingDelete) {
        if (pendingDelete != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Event deleted",
                actionLabel = "Undo",
            )
            when (result) {
                SnackbarResult.ActionPerformed -> homeVm.undoDelete()
                SnackbarResult.Dismissed -> homeVm.clearPendingDelete()
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Timeline") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                activeFilter?.let { quickLogVm.selectCategory(it) }
                showSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Log event")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = activeFilter == null,
                            onClick = { homeVm.setFilter(null) },
                            label = { Text("All") },
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = activeFilter?.id == cat.id,
                            onClick = {
                                homeVm.setFilter(if (activeFilter?.id == cat.id) null else cat)
                            },
                            label = { Text("${cat.emoji} ${cat.name}") },
                        )
                    }
                }
            }

            LazyColumn(
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
                                onSwipeDelete = { homeVm.swipeDelete(entry.event) },
                                onClick = { onNavigateToEventEdit(entry.event.id) },
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
                onDismiss = {
                    scope.launch { sheetState.hide() }
                    quickLogVm.reset()
                    showSheet = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEventRow(
    event: Event,
    category: com.trackr.app.domain.Category?,
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
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        },
    ) {
        EventRow(event = event, category = category, onClick = onClick)
    }
}

// @spec EL-UI-002
@Composable
private fun EventRow(event: Event, category: com.trackr.app.domain.Category?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category?.emoji ?: "",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(28.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.value?.let {
                    Text(formatValue(it), style = MaterialTheme.typography.bodyMedium)
                }
                event.notes?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = event.timestamp.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
            "Event deleted",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onUndo) { Text("Undo") }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// @spec EL-UI-013, EL-UI-030, EL-UI-032, EL-UI-034, EL-UI-052b, EL-UI-054, EL-UI-055b,
// EL-NAV-002, EL-PROC-001
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(
    categories: List<Category>,
    viewModel: QuickLogViewModel,
    onDismiss: () -> Unit,
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val value by viewModel.value.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val scope = rememberCoroutineScope()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.imagePath.value = it.toString() }
    }

    if (selectedCategory == null) {
        // Step 1 — Category picker
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Choose a category", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp),
            ) {
                items(categories) { cat ->
                    Button(
                        onClick = { viewModel.selectCategory(cat) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(cat.emoji, style = MaterialTheme.typography.headlineSmall)
                            Text(cat.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    } else {
        // Step 2 — Value + details
        val cat = selectedCategory!!
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${cat.emoji} ${cat.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.selectedCategory.value = null }) {
                    Text("Change")
                }
            }

            if (cat.valueType != ValueType.None) {
                ValueInputField(
                    value = value,
                    onValueChange = { viewModel.value.value = it },
                    valueType = cat.valueType,
                    autoFocus = true,
                )
                if (saveResult is SaveResult.ValidationError) {
                    Text(
                        "Value is required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            TextButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) {
                Text(if (viewModel.imagePath.value != null) "Photo added ✓" else "Add photo")
            }

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
