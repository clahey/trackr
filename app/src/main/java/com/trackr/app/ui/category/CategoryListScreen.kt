package com.trackr.app.ui.category

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// @spec CAT-UI-001, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006, CAT-UI-017
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryListScreen(
    onNavigateToCategoryEdit: (String?) -> Unit,
    pendingSnackbarMessage: StateFlow<String?> = MutableStateFlow(null),
    onSnackbarMessageConsumed: () -> Unit = {},
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()
    var menuCategoryId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarMessage by pendingSnackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onSnackbarMessageConsumed()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCategoryEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryRow(
                    category = category,
                    onClick = { onNavigateToCategoryEdit(category.id) },
                    onLongClick = { menuCategoryId = category.id },
                    menuExpanded = menuCategoryId == category.id,
                    onMenuDismiss = { menuCategoryId = null },
                    onDeleteClick = {
                        menuCategoryId = null
                        viewModel.deleteCategory(category.id)
                    },
                )
            }
        }
    }

    pendingDelete?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete category?") },
            text = {
                // @spec CAT-UI-005
                val text = if (confirmation.isMetaCategory) {
                    buildString {
                        if (confirmation.ownEventCount > 0) {
                            val n = confirmation.ownEventCount
                            append("$n ${if (n == 1) "event" else "events"} from this category will be permanently deleted.")
                        }
                        if (confirmation.subCategoryCount > 0) {
                            if (isNotEmpty()) append(" ")
                            val n = confirmation.subCategoryCount
                            append("$n ${if (n == 1) "subcategory" else "subcategories"} will be promoted to top-level categories.")
                        }
                    }
                } else {
                    val n = confirmation.ownEventCount
                    "This will permanently delete $n ${if (n == 1) "event" else "events"} logged under this category."
                }
                Text(text)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: Category,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    // @spec CAT-UI-001
    val startPadding = if (category is Category.SubCategory) 40.dp else 16.dp
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = startPadding, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = category.resolvedEmoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = category.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = valueTypeLabel(category.resolvedValueType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = onDeleteClick,
            )
        }
    }
}

internal fun valueTypeLabel(type: ValueType): String = when (type) {
    ValueType.None -> "None"
    ValueType.Scale -> "Scale (1–10)"
    ValueType.Boolean -> "Yes / No"
    ValueType.Number -> "Number"
    ValueType.Text -> "Text"
    ValueType.Duration -> "Duration"
    ValueType.Exercise -> "Exercise (sets × reps)"
    is ValueType.Unknown -> "Unknown"
}
