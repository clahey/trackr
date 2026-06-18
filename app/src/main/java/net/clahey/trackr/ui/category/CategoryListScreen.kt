package net.clahey.trackr.ui.category

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.annotation.StringRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import net.clahey.trackr.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.ValueType
import net.clahey.trackr.ui.theme.foregroundColorForBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// @spec CAT-UI-001, CAT-UI-003, CAT-UI-004, CAT-UI-005, CAT-UI-006,
// CAT-UI-051, CAT-UI-052, CAT-UI-017
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
    val pendingGroupPicker by viewModel.pendingGroupPicker.collectAsState()
    var menuCategoryId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val snackbarMessage by pendingSnackbarMessage.collectAsState()
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        onSnackbarMessageConsumed()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.categories_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToCategoryEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_category))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(categories, key = { it.id }) { category ->
                val hasSubCategories = category is Category.MetaCategory &&
                    categories.any { it is Category.SubCategory && it.parent.id == category.id }
                CategoryRow(
                    category = category,
                    hasSubCategories = hasSubCategories,
                    onClick = { onNavigateToCategoryEdit(category.id) },
                    onLongClick = { menuCategoryId = category.id },
                    menuExpanded = menuCategoryId == category.id,
                    onMenuDismiss = { menuCategoryId = null },
                    onDeleteClick = {
                        menuCategoryId = null
                        viewModel.deleteCategory(category.id)
                    },
                    onAddToGroupClick = {
                        menuCategoryId = null
                        viewModel.startAddToGroup(category.id)
                    },
                    onMoveToAnotherGroupClick = {
                        menuCategoryId = null
                        viewModel.startMoveToAnotherGroup(category.id)
                    },
                    onRemoveFromGroupClick = {
                        menuCategoryId = null
                        viewModel.removeFromGroup(category.id)
                    },
                )
            }
        }
    }

    pendingDelete?.let { confirmation ->
        DeleteCategoryDialog(
            confirmation = confirmation,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    // @spec CAT-UI-051, CAT-UI-052
    pendingGroupPicker?.let { state ->
        GroupPickerDialog(
            state = state,
            onSelect = { parentId -> viewModel.reparentCategory(state.categoryId, parentId) },
            onCreateNewGroup = { name -> viewModel.reparentWithNewGroup(state.categoryId, name) },
            onDismiss = { viewModel.dismissGroupPicker() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRow(
    category: Category,
    hasSubCategories: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onDeleteClick: () -> Unit,
    onAddToGroupClick: () -> Unit,
    onMoveToAnotherGroupClick: () -> Unit,
    onRemoveFromGroupClick: () -> Unit,
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
            // @spec CAT-UI-050
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = Color(category.resolvedColor),
                        shape = CircleShape,
                    ),
            ) {
                Text(
                    text = category.resolvedEmoji,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(foregroundColorForBackground(category.resolvedColor)),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = category.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(valueTypeStringRes(category.resolvedValueType)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // @spec CAT-UI-003
        DropdownMenu(expanded = menuExpanded, onDismissRequest = onMenuDismiss) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = onDeleteClick,
            )
            if (category is Category.MetaCategory && !hasSubCategories) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_menu_add_to_group)) },
                    onClick = onAddToGroupClick,
                )
            }
            if (category is Category.SubCategory) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_menu_move_to_group)) },
                    onClick = onMoveToAnotherGroupClick,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.category_menu_remove_from_group)) },
                    onClick = onRemoveFromGroupClick,
                )
            }
        }
    }
}

@Composable
private fun GroupPickerDialog(
    state: GroupPickerState,
    onSelect: (parentId: String) -> Unit,
    onCreateNewGroup: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showNameEntry by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    if (showNameEntry) {
        AlertDialog(
            onDismissRequest = { showNameEntry = false },
            title = { Text(stringResource(R.string.category_group_new_name_title)) },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    label = { Text(stringResource(R.string.category_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreateNewGroup(newGroupName.trim()) },
                    enabled = newGroupName.isNotBlank(),
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameEntry = false }) { Text(stringResource(R.string.action_back)) }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(if (state.isMoveOperation) R.string.category_group_picker_title_move else R.string.category_group_picker_title_add)) },
            text = {
                Column {
                    state.eligibleParents.forEach { parent ->
                        TextButton(
                            onClick = { onSelect(parent.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = parent.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    TextButton(
                        onClick = { newGroupName = ""; showNameEntry = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.category_group_create),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@StringRes
internal fun valueTypeStringRes(type: ValueType): Int = when (type) {
    ValueType.None -> R.string.value_type_none
    ValueType.Scale -> R.string.value_type_scale
    ValueType.Boolean -> R.string.value_type_boolean
    ValueType.Number -> R.string.value_type_number
    ValueType.Text -> R.string.value_type_text
    ValueType.Duration -> R.string.value_type_duration
    ValueType.Exercise -> R.string.value_type_exercise
    is ValueType.Unknown -> R.string.value_type_unknown
}
