package com.secureguard.enterprise.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.util.formatTime

@Composable
fun AssetCard(
    asset: Asset,
    onClick: () -> Unit,
    onSearch: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when (asset.status) {
                            AssetStatus.ONLINE -> "🟢"
                            AssetStatus.MAINTENANCE -> "🟡"
                            AssetStatus.OFFLINE -> "🔴"
                            else -> "⚪"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(asset.shortName, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "📍 ${asset.latitude ?: "Unbekannt"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "📶 RSSI: ${asset.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall
                )
                asset.lastSeen?.let {
                    Text(
                        text = "⏱ ${it.formatTime()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (asset.status == AssetStatus.OFFLINE && onSearch != null) {
                Button(
                    onClick = onSearch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("🔍 Suchen")
                }
            }
        }
    }
}
