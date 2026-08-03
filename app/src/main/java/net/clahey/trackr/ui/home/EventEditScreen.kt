package net.clahey.trackr.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.clahey.trackr.R
import net.clahey.trackr.domain.Category
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.UnsavedChangesDialog
import net.clahey.trackr.ui.components.ValueInputField
import net.clahey.trackr.ui.components.TimestampField
import net.clahey.trackr.ui.components.ValueUIState

// @spec EL-UI-040, EL-UI-042, EL-UI-043, EL-UI-044, EL-UI-044a, EL-UI-044b, EL-UI-045,
// EL-NAV-005, EL-NAV-006, EL-NAV-009, EL-NAV-010, EL-NAV-012, EL-PROC-002, APP-NAV-003
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onNavigateBack: (errorMessage: String?) -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    val timestamp by viewModel.timestamp.collectAsState()
    val value by viewModel.value.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val imagePaths by viewModel.imagePaths.collectAsState()
    val category by viewModel.category.collectAsState()
    val pendingDelete by viewModel.pendingDelete.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val deleteComplete by viewModel.deleteComplete.collectAsState()
    val navigateBack by viewModel.navigateBack.collectAsState()
    val eventIds by viewModel.eventIds.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isDirty by viewModel.isDirty.collectAsState()
    val prevEventState by viewModel.prevEventState.collectAsState()
    val nextEventState by viewModel.nextEventState.collectAsState()
    val showDiscardDialog by viewModel.showDiscardDialog.collectAsState()

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var showBackDiscardDialog by remember { mutableStateOf(false) }

    // @spec EL-NAV-013
    BackHandler(enabled = isDirty) { showBackDiscardDialog = true }

    // pageCount = 1 when dirty so the pager rubber-bands naturally instead of navigating.
    // @spec EL-NAV-009, EL-NAV-010
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { if (isDirty) 1 else maxOf(1, eventIds.size) },
    )

    // Keep pager position in sync with ViewModel's currentIndex.
    LaunchedEffect(isDirty, currentIndex) {
        pagerState.scrollToPage(if (isDirty) 0 else currentIndex)
    }

    LaunchedEffect(Unit) {
        launch {
            // @spec EL-NAV-012
            var wasScrolling = false
            snapshotFlow { pagerState.isScrollInProgress }
                .collect { scrolling ->
                    if (scrolling) wasScrolling = true
                    else if (wasScrolling) { wasScrolling = false; viewModel.scrollEnded() }
                }
        }
        // @spec EL-NAV-009, EL-NAV-011
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page -> viewModel.pageSettled(page) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath ?: return@rememberLauncherForActivityResult
        if (success) viewModel.addImage(path) else viewModel.cancelImage(path)
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
                withContext(Dispatchers.Main) { viewModel.addImage(path) }
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

    val eventNotFound = stringResource(R.string.event_not_found)
    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack(eventNotFound)
    }
    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) onNavigateBack(null)
    }
    LaunchedEffect(deleteComplete) {
        if (deleteComplete) onNavigateBack(null)
    }

    if (pendingDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(R.string.event_delete_title)) },
            text = { Text(stringResource(R.string.event_delete_message)) },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.confirmDelete() } }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text(stringResource(R.string.add_image_dialog_title)) },
            confirmButton = {
                TextButton(onClick = { showImageSourceDialog = false; launchCamera() }) {
                    Text(stringResource(R.string.action_take_photo))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.action_choose_from_gallery)) }
            },
        )
    }

    // @spec EL-NAV-013
    if (showBackDiscardDialog) {
        UnsavedChangesDialog(
            onSave = { scope.launch { viewModel.save() } },
            onDiscard = { viewModel.cancel(); onNavigateBack(null) },
            onCancel = { showBackDiscardDialog = false },
        )
    }

    // @spec EL-NAV-012
    if (showDiscardDialog) {
        UnsavedChangesDialog(
            onSave = { scope.launch { viewModel.saveInPlace() } },
            onDiscard = { viewModel.discardInPlace() },
            onCancel = { viewModel.dismissDiscardDialog() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_edit_title)) },
                navigationIcon = {
                    // @spec EL-NAV-013
                    IconButton(onClick = {
                        if (isDirty) showBackDiscardDialog = true
                        else { viewModel.cancel(); onNavigateBack(null) }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestDelete() }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
            ) { pageIndex ->
                if (isDirty) {
                    EventFormContent(
                        timestamp = timestamp,
                        valueUIState = value,
                        notes = notes,
                        imagePaths = imagePaths,
                        category = category,
                        readOnly = false,
                        showSave = true,
                        onTimestampChange = { viewModel.setTimestamp(it) },
                        onValueChange = { viewModel.setValue(it) },
                        onDone = { scope.launch { viewModel.save() } },
                        onNotesChange = { viewModel.setNotes(it) },
                        onAddImage = { showImageSourceDialog = true },
                        onRemoveImage = { viewModel.removeImage(it) },
                        onSave = { scope.launch { viewModel.save() } },
                    )
                } else {
                    when (pageIndex) {
                        currentIndex -> EventFormContent(
                            timestamp = timestamp,
                            valueUIState = value,
                            notes = notes,
                            imagePaths = imagePaths,
                            category = category,
                            readOnly = false,
                            showSave = false,
                            // @spec EL-NAV-012
                            onTimestampChange = { viewModel.setTimestamp(it) },
                            onValueChange = { viewModel.setValue(it) },
                            onDone = { scope.launch { viewModel.save() } },
                            onNotesChange = { viewModel.setNotes(it) },
                            onAddImage = { showImageSourceDialog = true },
                            onRemoveImage = { viewModel.removeImage(it) },
                            onSave = { scope.launch { viewModel.save() } },
                        )
                        currentIndex - 1 -> prevEventState?.let { state ->
                            EventFormContent(
                                timestamp = state.event.timestamp,
                                valueUIState = state.valueUIState,
                                notes = state.event.notes ?: "",
                                imagePaths = state.event.imagePaths,
                                category = state.category,
                            )
                        } ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        currentIndex + 1 -> nextEventState?.let { state ->
                            EventFormContent(
                                timestamp = state.event.timestamp,
                                valueUIState = state.valueUIState,
                                notes = state.event.notes ?: "",
                                imagePaths = state.event.imagePaths,
                                category = state.category,
                            )
                        } ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                        else -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventFormContent(
    timestamp: Instant,
    valueUIState: ValueUIState,
    notes: String,
    imagePaths: List<String>,
    category: Category?,
    readOnly: Boolean = true,
    showSave: Boolean = false,
    onTimestampChange: (Instant) -> Unit = {},
    onValueChange: (ValueUIState) -> Unit = {},
    onDone: () -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    onAddImage: () -> Unit = {},
    onRemoveImage: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // @spec EL-UI-046
        category?.let { cat ->
            Text(
                text = "${cat.resolvedEmoji} ${cat.name}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        TimestampField(
            timestamp = timestamp,
            onTimestampChange = onTimestampChange,
            enabled = !readOnly,
        )

        ValueInputField(
            uiState = valueUIState,
            onStateChange = onValueChange,
            onDone = onDone,
        )

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            readOnly = readOnly,
            label = { Text(stringResource(R.string.event_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        // @spec EL-UI-044a, EL-UI-044b
        imagePaths.forEach { path ->
            Box {
                AsyncImage(
                    model = File(path).toUri(),
                    contentDescription = stringResource(R.string.cd_attached_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                IconButton(
                    onClick = { onRemoveImage(path) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_remove_image), tint = Color.White)
                }
            }
        }

        TextButton(onClick = onAddImage) { Text(stringResource(R.string.action_add_image)) }

        if (showSave) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

