package com.secureguard.enterprise.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.secureguard.enterprise.data.model.AuditLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

/**
 * Zeile im Aktivitätsverlauf: Aktion, Details, Zeitstempel, Ausführender.
 * Farblich markiert: Aktionen (Akzent), Agent (Primär), Fehler (Rot).
 */
@Composable
fun ActivityLogItem(entry: AuditLog, modifier: Modifier = Modifier) {
    val isError = entry.details.contains("success=false", ignoreCase = true) ||
        entry.details.contains("Fehler", ignoreCase = true)
    val actionColor = when {
        isError -> MaterialTheme.colorScheme.error
        entry.action.startsWith("ACTION") -> Color(0xFF26A69A)
        entry.action.startsWith("AGENT") -> MaterialTheme.colorScheme.primary
        entry.action.startsWith("REGISTER") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.action,
                    style = MaterialTheme.typography.labelLarge,
                    color = actionColor
                )
                if (entry.details.isNotEmpty()) {
                    Text(
                        text = entry.details.take(90),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = timeFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.userId,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
