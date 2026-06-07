package net.clahey.trackr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import java.io.File
import net.clahey.trackr.domain.Category
import net.clahey.trackr.domain.Event
import net.clahey.trackr.ui.theme.foregroundColorForBackground
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// @spec EL-UI-002, EL-UI-004, EL-UI-005, EL-UI-061, EL-UI-079, THEME-UI-011
@Composable
fun EventRow(event: Event, category: Category, hasMismatch: Boolean, onClick: (() -> Unit)?) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp)
    if (onClick != null) {
        ElevatedCard(onClick = onClick, modifier = modifier) { EventRowContent(event, category, hasMismatch) }
    } else {
        ElevatedCard(modifier = modifier) { EventRowContent(event, category, hasMismatch) }
    }
}

@Composable
private fun EventRowContent(event: Event, category: Category, hasMismatch: Boolean) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            event.value?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatValue(it), style = MaterialTheme.typography.bodyMedium)
                    if (hasMismatch) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Value type mismatch",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        )
                    }
                }
            }
            event.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val firstImage = event.imagePaths.firstOrNull()
        if (firstImage != null) {
            Spacer(modifier = Modifier.width(8.dp))
            AsyncImage(
                model = File(firstImage).toUri(),
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = event.timestamp.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
