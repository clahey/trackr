package com.trackr.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.trackr.app.domain.Category
import com.trackr.app.domain.Event
import com.trackr.app.domain.ValueType
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.components.ValueInputField
import com.trackr.app.ui.components.formatValue
import com.trackr.app.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            // @spec EL-UI-013, EL-UI-075
            FloatingActionButton(onClick = {
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
                Icon(Icons.Default.Add, contentDescription = "Log event")
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
                            selected = activeFilter is ActiveFilter.All,
                            onClick = { homeVm.setFilter(ActiveFilter.All) },
                            label = { Text("All") },
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
                            FilterChip(
                                selected = isMetaSelected,
                                onClick = {
                                    val f = activeFilter
                                    when {
                                        f is ActiveFilter.TopLevel && f.category.id == meta.id ->
                                            homeVm.setFilter(ActiveFilter.All)
                                        else -> homeVm.setFilter(ActiveFilter.TopLevel(meta))
                                    }
                                },
                                label = { Text("${meta.resolvedEmoji} ${meta.name}") },
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
                                FilterChip(
                                    selected = isSubSelected,
                                    onClick = {
                                        homeVm.setFilter(
                                            if (isSubSelected) ActiveFilter.TopLevel(meta)
                                            else ActiveFilter.Sub(meta, sub)
                                        )
                                    },
                                    label = { Text("${sub.resolvedEmoji} ${sub.name}") },
                                )
                            }
                        }
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
                                hasMismatch = entry.hasMismatch,
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
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        },
    ) {
        EventRow(event = event, category = category, hasMismatch = hasMismatch, onClick = onClick)
    }
}

// @spec EL-UI-002, EL-UI-004, EL-UI-005, EL-UI-061, THEME-UI-011
@Composable
private fun EventRow(event: Event, category: com.trackr.app.domain.Category?, hasMismatch: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // @spec EL-UI-005, THEME-UI-011
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = category?.let { Color(it.resolvedColor) } ?: MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            ) {
                Text(
                    text = category?.resolvedEmoji ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    color = category?.let { Color(foregroundColorForBackground(it.resolvedColor)) }
                        ?: MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                event.value?.let {
                    // @spec EL-UI-061
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatValue(it), style = MaterialTheme.typography.bodyMedium)
                        if (hasMismatch) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Value type mismatch",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp).padding(start = 4.dp),
                            )
                        }
                    }
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

// @spec EL-UI-013, EL-UI-030, EL-UI-031a, EL-UI-031b, EL-UI-032, EL-UI-034, EL-UI-052b,
// EL-UI-054, EL-UI-055b, EL-UI-072, EL-UI-073, EL-UI-074, EL-NAV-002, EL-PROC-001
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickLogSheet(
    categories: List<Category>,
    viewModel: QuickLogViewModel,
    onDismiss: () -> Unit,
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val expandedMetaCategoryId by viewModel.expandedMetaCategoryId.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val imagePath by viewModel.imagePath.collectAsState()
    val value by viewModel.value.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath ?: return@rememberLauncherForActivityResult
        if (success) viewModel.commitImage(path) else viewModel.cancelImage(path)
        pendingCameraPath = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val path = viewModel.createImageFile()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.File(path).outputStream().use { input.copyTo(it) }
                }
                withContext(Dispatchers.Main) { viewModel.commitImage(path) }
            } catch (_: Exception) {
                viewModel.cancelImage(path)
            }
        }
    }

    fun launchCamera() {
        val path = viewModel.createImageFile()
        pendingCameraPath = path
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", java.io.File(path)
        )
        cameraLauncher.launch(uri)
    }

    if (showImageSourceDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Add image") },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false; launchCamera() }) {
                    Text("Take photo")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose from gallery") }
            },
        )
    }

    if (selectedCategory == null) {
        // Step 1 — Category picker
        val metaCategories = categories.filterIsInstance<Category.MetaCategory>()
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Choose a category", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            // @spec EL-UI-072, EL-UI-073, EL-UI-074
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(300.dp),
            ) {
                metaCategories.forEach { meta ->
                    val subCats = categories.filterIsInstance<Category.SubCategory>()
                        .filter { it.parent.id == meta.id }
                    val isExpanded = expandedMetaCategoryId == meta.id && subCats.isNotEmpty()
                    if (isExpanded) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "meta-${meta.id}") {
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "${meta.resolvedEmoji} ${meta.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Button(
                                        onClick = { viewModel.selectCategory(meta) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(meta.resolvedColor),
                                            contentColor = Color(foregroundColorForBackground(meta.resolvedColor)),
                                        ),
                                    ) { Text("Log to ${meta.name} directly") }
                                    subCats.forEach { sub ->
                                        TextButton(
                                            onClick = { viewModel.selectCategory(sub) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("${sub.resolvedEmoji} ${sub.name}") }
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "meta-${meta.id}") {
                            Button(
                                onClick = {
                                    if (subCats.isNotEmpty()) viewModel.expandMetaCategory(meta.id)
                                    else viewModel.selectCategory(meta)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(meta.resolvedColor),
                                    contentColor = Color(foregroundColorForBackground(meta.resolvedColor)),
                                ),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(meta.resolvedEmoji, style = MaterialTheme.typography.headlineSmall)
                                    Text(meta.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
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
                Text("${cat.resolvedEmoji} ${cat.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.selectedCategory.value = null }) {
                    Text("Change")
                }
            }

            if (cat.resolvedValueType != ValueType.None) {
                ValueInputField(
                    value = value,
                    onValueChange = { viewModel.value.value = it },
                    valueType = cat.resolvedValueType,
                    autoFocus = true,
                    defaultUnit = cat.unit,
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

            // @spec EL-UI-031a, EL-UI-031b
            if (imagePath == null) {
                TextButton(onClick = { showImageSourceDialog = true }) { Text("Add image") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo added ✓", modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.removeImage() }) { Text("Remove") }
                    TextButton(onClick = { showImageSourceDialog = true }) { Text("Replace") }
                }
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
