package com.secureguard.enterprise.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.agent.NodeStatus

/**
 * Statuszeile eines API-Abfrageknotens (Node-Status-Screen):
 * Status-Kugel, Knoten-ID, Status-Text, Ein/Aus-Schalter.
 */
@Composable
fun NodeStatusItem(
    nodeId: String,
    status: NodeStatus,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            when (status) {
                                NodeStatus.ONLINE -> Color(0xFF4CAF50)
                                NodeStatus.OFFLINE -> Color(0xFFF44336)
                                NodeStatus.ERROR -> Color(0xFFFFC107)
                                NodeStatus.RATE_LIMITED -> Color(0xFFFF9800)
                                NodeStatus.UNKNOWN -> Color.Gray
                            },
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(nodeId, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}
