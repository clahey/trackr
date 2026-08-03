package net.clahey.trackr.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.clahey.trackr.R
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.SaveResult
import net.clahey.trackr.ui.components.TimestampField
import net.clahey.trackr.ui.components.ValueInputField

// @spec EL-UI-013, EL-UI-030, EL-UI-031a, EL-UI-031b, EL-UI-032, EL-UI-034, EL-UI-052b,
@Composable
private fun CategoryGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(300.dp),
        content = content,
    )
}

// @spec EL-UI-095
@Composable
private fun CategoryTile(emoji: String?, name: String, color: Long, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(3.dp, Color(color)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (emoji != null) Text(emoji, style = MaterialTheme.typography.headlineSmall)
            // Auto-shrink long names to one line (e.g. "Medication" in the narrow 3-column grid).
            AutoShrinkLabel(name, MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// @spec EL-UI-090, EL-UI-091 — full-width "+ New …" tile at the end of the grid
@Composable
private fun AddCategoryTile(name: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// Shrink the label down to a floor to fit on one line, then ellipsize if it still overflows
// (BOM 2025.01.01 predates BasicText autoSize, so measure-and-step manually).
@Composable
private fun AutoShrinkLabel(text: String, style: TextStyle, minFontSize: TextUnit = 9.sp) {
    val maxFontSize = if (style.fontSize != TextUnit.Unspecified) style.fontSize else 14.sp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
        val fontSize = remember(text, maxWidthPx, style) {
            var size = maxFontSize
            while (size.value > minFontSize.value) {
                val laid = measurer.measure(
                    text = text,
                    style = style.copy(fontSize = size),
                    maxLines = 1,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
                if (!laid.hasVisualOverflow) break
                size = (size.value - 1f).sp
            }
            size
        }
        Text(
            text,
            style = style.copy(fontSize = fontSize),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// EL-UI-054, EL-UI-055b, EL-UI-072, EL-UI-073, EL-UI-074, EL-NAV-002, EL-PROC-001
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun QuickLogSheet(
    categories: List<Category>,
    viewModel: QuickLogViewModel,
    onNavigateToCreateCategory: () -> Unit,
    onNavigateToCreateSubCategory: (parentId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val expandedMetaCategoryId by viewModel.expandedMetaCategoryId.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val imagePath by viewModel.imagePath.collectAsState()
    val timestamp by viewModel.timestamp.collectAsState()
    val value by viewModel.value.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BackHandler(enabled = selectedCategory != null) { viewModel.selectedCategory.value = null }
    BackHandler(enabled = selectedCategory == null && expandedMetaCategoryId != null) {
        viewModel.expandMetaCategory(null)
    }

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

    if (selectedCategory == null) {
        // Step 1 — Category picker
        val metaCategories = categories.filterIsInstance<Category.MetaCategory>()
        val expandedMeta = metaCategories.find { it.id == expandedMetaCategoryId }
        val expandedSubCats = expandedMeta?.let { meta ->
            categories.filterIsInstance<Category.SubCategory>().filter { it.parent.id == meta.id }
        }

        // A ModalBottomSheet renders in its own window that doesn't inherit testTagsAsResourceId
        // from the nav-host root, so its testTags aren't visible to uiautomator/screenshot tooling.
        // Re-enable it here and tag the picker so the tooling can confirm the sheet opened.
        Column(
            modifier = Modifier
                .semantics { testTagsAsResourceId = true }
                .testTag("quick_log_sheet")
                .padding(16.dp)
        ) {
            if (expandedMeta != null && expandedSubCats != null) {
                // Drill-down: subcategory picker for the selected MetaCategory
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.expandMetaCategory(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        "${expandedMeta.resolvedEmoji} ${expandedMeta.name}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // @spec EL-UI-072, EL-UI-073, EL-UI-074
                CategoryGrid {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CategoryTile(
                            emoji = null,
                            name = stringResource(R.string.quick_log_log_directly, expandedMeta.name),
                            color = expandedMeta.resolvedColor,
                            onClick = { viewModel.selectCategory(expandedMeta) },
                        )
                    }
                    items(expandedSubCats, key = { it.id }) { sub ->
                        CategoryTile(
                            emoji = sub.resolvedEmoji ?: expandedMeta.resolvedEmoji,
                            name = sub.name,
                            color = sub.resolvedColor,
                            onClick = { viewModel.selectCategory(sub) },
                        )
                    }
                    // @spec EL-UI-091, EL-NAV-020
                    item(key = "add-subcategory", span = { GridItemSpan(maxLineSpan) }) {
                        AddCategoryTile(stringResource(R.string.quick_log_new_subcategory)) {
                            viewModel.beginCategoryCreate()
                            onNavigateToCreateSubCategory(expandedMeta.id)
                        }
                    }
                }
            } else {
                // Top-level MetaCategory grid
                Text(stringResource(R.string.quick_log_choose_category), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                // @spec EL-UI-072, EL-UI-073, EL-UI-074
                CategoryGrid {
                    items(metaCategories, key = { it.id }) { meta ->
                        val subCats = categories.filterIsInstance<Category.SubCategory>()
                            .filter { it.parent.id == meta.id }
                        CategoryTile(
                            emoji = meta.resolvedEmoji,
                            name = meta.name,
                            color = meta.resolvedColor,
                            onClick = {
                                if (subCats.isNotEmpty()) viewModel.expandMetaCategory(meta.id)
                                else viewModel.selectCategory(meta)
                            },
                        )
                    }
                    // @spec EL-UI-090, EL-NAV-020
                    item(key = "add-category", span = { GridItemSpan(maxLineSpan) }) {
                        AddCategoryTile(stringResource(R.string.quick_log_new_category)) {
                            viewModel.beginCategoryCreate()
                            onNavigateToCreateCategory()
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
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.selectedCategory.value = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text("${cat.resolvedEmoji} ${cat.name}", style = MaterialTheme.typography.titleMedium)
            }

            if (cat.resolvedValueType != ValueType.None) {
                ValueInputField(
                    uiState = value,
                    onStateChange = { viewModel.updateValue(it) },
                    autoFocus = true,
                    onDone = { scope.launch { viewModel.save() } },
                )
                if (saveResult is SaveResult.ValidationError) {
                    Text(
                        stringResource(R.string.value_required_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { viewModel.notes.value = it },
                label = { Text(stringResource(R.string.event_field_notes_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // @spec EL-UI-031a, EL-UI-031b
            if (imagePath == null) {
                TextButton(onClick = { showImageSourceDialog = true }) { Text(stringResource(R.string.action_add_image)) }
            } else {
                AsyncImage(
                    model = File(imagePath!!).toUri(),
                    contentDescription = "Attached photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.removeImage() }) { Text(stringResource(R.string.action_remove)) }
                    TextButton(onClick = { showImageSourceDialog = true }) { Text(stringResource(R.string.action_replace)) }
                }
            }

            // @spec EL-UI-031, EL-UI-032
            TimestampField(
                timestamp = timestamp,
                onTimestampChange = { viewModel.timestamp.value = it },
            )

            Button(
                onClick = { scope.launch { viewModel.save() } },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_save))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
