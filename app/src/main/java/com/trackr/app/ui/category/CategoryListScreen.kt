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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.trackr.app.domain.Category
import com.trackr.app.domain.ValueType

// @spec CAT-UI-001, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryListScreen(
    onNavigateToCategoryEdit: (String?) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val pendingDelete by viewModel.pendingDeleteConfirmation.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
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
                    onLongClick = { viewModel.deleteCategory(category.id) },
                )
            }
        }
    }

    pendingDelete?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete category?") },
            text = {
                Text("This will permanently delete ${confirmation.eventCount} event(s) logged under this category.")
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = category.emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = category.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueTypeLabel(category.valueType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun valueTypeLabel(type: ValueType): String = when (type) {
    ValueType.None -> "None"
    ValueType.Scale -> "Scale (1–10)"
    ValueType.Boolean -> "Yes / No"
    ValueType.Number -> "Number"
    ValueType.Text -> "Text"
    ValueType.Duration -> "Duration"
    is ValueType.Unknown -> "Unknown"
}
