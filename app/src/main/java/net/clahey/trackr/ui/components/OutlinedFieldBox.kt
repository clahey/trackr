package net.clahey.trackr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// @spec CAT-UI-074, CAT-UI-075, CAT-UI-076, CAT-UI-018
@Composable
fun OutlinedFieldBox(
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    // This box draws its own border and label, so it has no disabled state to inherit. The colors
    // below are OutlinedTextField's disabled tokens, so a disabled box matches a disabled field
    // sitting next to it. Disabled wins over isError: a form that can't be edited can't be
    // submitted, so there is no error to report.
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val labelColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = MaterialTheme.shapes.extraSmall

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .then(if (isError) Modifier.semantics { error("$label has an error") } else Modifier),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, shape)
                .padding(16.dp),
            content = content,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp)
                .offset(y = (-8).dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp),
        )
    }
}
