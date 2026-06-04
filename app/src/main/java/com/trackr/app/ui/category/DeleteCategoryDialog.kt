package com.trackr.app.ui.category

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.trackr.app.R

// @spec CAT-UI-005
@Composable
fun DeleteCategoryDialog(
    confirmation: DeleteConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_category_title)) },
        text = {
            val sentences = buildList {
                if (confirmation.ownEventCount > 0) {
                    val n = confirmation.ownEventCount
                    add(pluralStringResource(R.plurals.delete_category_events_message, n, n))
                }
                if (confirmation.subCategoryCount > 0) {
                    val n = confirmation.subCategoryCount
                    add(pluralStringResource(R.plurals.delete_category_subcategories_message, n, n))
                }
            }
            Text(sentences.joinToString(" "))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete_category_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.delete_category_cancel)) }
        },
    )
}
