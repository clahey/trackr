package com.trackr.app.ui.category

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

// @spec CAT-UI-005
@Composable
fun DeleteCategoryDialog(
    confirmation: DeleteConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete category?") },
        text = {
            val text = buildString {
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
            Text(text)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
