package net.clahey.trackr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * How a [ReminderPermissionProblem] is put in front of the user: an error-toned row naming the
 * problem, which opens the settings screen that fixes it.
 *
 * The category list's ambient banner and the Reminder section's inline prompt both render this, so
 * the same problem reads the same way wherever it surfaces; only [shape] and placement differ.
 */
// @spec REM-PERM-002, REM-PERM-004
@Composable
fun ReminderPermissionNotice(
    problem: ReminderPermissionProblem,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .clickable { context.startActivity(problem.settingsIntent(context)) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Text(
                stringResource(problem.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
