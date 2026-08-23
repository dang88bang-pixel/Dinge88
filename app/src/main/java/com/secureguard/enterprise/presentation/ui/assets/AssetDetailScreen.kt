package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AssetDetailScreen(
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    val asset by viewModel.getAsset(assetId).collectAsState(initial = null)
    val telemetry by viewModel.getLatestTelemetry(assetId).collectAsState(initial = null)

    asset?.let { currentAsset ->
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Asset: ${currentAsset.name}")
            Text("MAC: ${currentAsset.mac}")
            Text("Status: ${currentAsset.status}")
            
            telemetry?.let { data ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("📊 Telemetrie (Live):")
                Text("  🔋 Batterie: ${data.battery}%")
                Text("  ⛽ Kraftstoff: ${data.fuel}L")
                Text("  🏃 Motor: ${data.motor} U/min")
                Text("  ⏱️ Betriebsstunden: ${data.operatingHours}h")
                Text("  📍 Entfernung: ${data.distance}km")
            } ?: run {
                Text("⏳ Telemetrie wird geladen...")
            }
        }
    }
}