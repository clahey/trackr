package com.trackr.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.ui.SaveResult
import com.trackr.app.ui.components.ValueInputField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

// @spec EL-UI-040, EL-UI-042, EL-UI-043, EL-UI-044, EL-UI-044a, EL-UI-044b, EL-UI-045,
// EL-NAV-005, EL-NAV-006, EL-PROC-002, APP-NAV-003
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

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(navigateBack) {
        if (navigateBack) onNavigateBack("Event not found.")
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
            title = { Text("Delete event?") },
            text = { Text("This event will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { scope.launch { viewModel.confirmDelete() } }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            },
        )
    }

    if (showImageSourceDialog) {
        AlertDialog(
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Event") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancel()
                        onNavigateBack(null)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.requestDelete() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
            OutlinedTextField(
                value = timestampFormatter.format(timestamp),
                onValueChange = {},
                readOnly = true,
                label = { Text("Timestamp") },
                modifier = Modifier.fillMaxWidth(),
            )

            ValueInputField(
                value = value,
                onValueChange = { viewModel.value.value = it },
                valueType = category?.resolvedValueType,
                defaultUnit = category?.unit,
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            // @spec EL-UI-044a, EL-UI-044b
            if (imagePaths.isNotEmpty()) {
                Text("Images (${imagePaths.size})", style = MaterialTheme.typography.labelMedium)
                imagePaths.forEach { path ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { viewModel.removeImage(path) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove image")
                        }
                    }
                }
            }
            TextButton(onClick = { showImageSourceDialog = true }) { Text("Add image") }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
