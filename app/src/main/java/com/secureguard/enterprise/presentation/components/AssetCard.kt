package com.secureguard.enterprise.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (asset.status) {
        AssetStatus.ONLINE -> Color(0xFF2E7D32)
        AssetStatus.MAINTENANCE -> Color(0xFFF9A825)
        AssetStatus.OFFLINE -> Color(0xFFC62828)
        AssetStatus.SEARCHING -> Color(0xFF1565C0)
        AssetStatus.UNKNOWN -> Color.Gray
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.06f)
        )
    ) {
        val a11yStatus = com.secureguard.enterprise.util.AccessibilityHelper
            .contentDescriptionForStatus(asset.status.name)
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(statusColor, CircleShape)
                            .semantics { contentDescription = a11yStatus }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        asset.shortName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "📶 ${asset.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val loc = when {
                    asset.latitude != null && asset.longitude != null ->
                        "📍 ${"%.4f".format(asset.latitude)}, ${"%.4f".format(asset.longitude)}"
                    else -> "📍 Unbekannt"
                }
                Text(loc, style = MaterialTheme.typography.bodySmall)
                asset.batteryLevel?.let {
                    Text("🔋 $it%", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "⏱ ${asset.lastSeen.formatTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (asset.maintenanceDue) {
                    Text(
                        "🔧 Wartung fällig",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
                if (asset.status == AssetStatus.OFFLINE) {
                    Text(
                        "⚠️ Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }
        }
    }
}

private fun Date?.formatTime(): String {
    if (this == null) return "Nie"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(this)
}
