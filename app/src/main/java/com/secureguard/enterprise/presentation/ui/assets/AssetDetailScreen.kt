package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.ui.common.ActionType
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(assetId) {
        viewModel.loadAsset(assetId)
    }

    val asset by viewModel.assetState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.shortName ?: "Asset") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTelemetry() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Telemetrie aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Asset-Info Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            asset?.name ?: "Laden...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("MAC: ${asset?.mac ?: "–"}", style = MaterialTheme.typography.bodyMedium)
                        asset?.vin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val statusColor = when (asset?.status) {
                                AssetStatus.ONLINE -> Color(0xFF2E7D32)
                                AssetStatus.OFFLINE -> Color(0xFFC62828)
                                AssetStatus.MAINTENANCE -> Color(0xFFF9A825)
                                else -> Color.Gray
                            }
                            Text(
                                "Status: ${asset?.status?.name ?: "–"}",
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(if (asset?.rssi == 0 || asset?.rssi == null) "📶 –" else "📶 ${asset.rssi} dBm")
                        }
                        asset?.let { a ->
                            if (a.latitude != null && a.longitude != null) {
                                Text(
                                    "📍 ${"%.5f".format(a.latitude)}, ${"%.5f".format(a.longitude)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        asset?.lastSeen?.let {
                            Text(
                                "Zuletzt gesehen: ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Telemetrie Card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "📊 Telemetrie",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        telemetry?.let { data ->
                            Text("🔋 Batterie: ${data.batteryPercent ?: "–"}%")
                            Text("⛽ Kraftstoff: ${data.fuelPercent ?: "–"}%")
                            Text("🏃 Motor: ${if (data.motorOk) "✅ OK" else "❌ Fehler"}")
                            Text("🛞 Reifen: ${if (data.tiresOk) "✅ OK" else "⚠️ Prüfung nötig"}")
                            Text("⏱ Betriebsstunden: ${"%.1f".format(data.operatingHours ?: 0.0)}h")
                            Text("📍 Kilometerstand: ${"%.1f".format(data.kilometers ?: 0.0)} km")
                        } ?: Text("⏳ Telemetrie wird geladen...")
                    }
                }
            }

            // Such-Buttons
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "🔍 Suche",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        if (isSearching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.height(24.dp).padding(end = 8.dp))
                                Text("Suche läuft...")
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.startSearch() }) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Text(" Alle", modifier = Modifier.padding(start = 4.dp))
                                }
                                Button(onClick = { viewModel.startExternalSearch() }) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null)
                                    Text(" Crowd", modifier = Modifier.padding(start = 4.dp))
                                }
                                Button(onClick = { viewModel.startSatelliteSearch() }) {
                                    Icon(Icons.Default.Satellite, contentDescription = null)
                                    Text(" Sat", modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        searchResult?.let { result ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (result.found) "✅ Asset gefunden (${result.detection?.sourceType?.name})"
                                else "❌ Asset nicht gefunden",
                                color = if (result.found) Color(0xFF2E7D32) else Color(0xFFC62828),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Aktions-Buttons
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚡ Aktionen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.executeAction(ActionType.ALARM) }) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Text(" Alarm", modifier = Modifier.padding(start = 4.dp))
                            }
                            Button(onClick = { viewModel.executeAction(ActionType.LIGHT) }) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null)
                                Text(" Licht", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.executeAction(ActionType.MOTOR_OFF) }) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                                Text(" Motor", modifier = Modifier.padding(start = 4.dp))
                            }
                            Button(onClick = { viewModel.executeAction(ActionType.MESSAGE) }) {
                                Icon(Icons.Default.Message, contentDescription = null)
                                Text(" Nachricht", modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        actionResult?.let { result ->
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${if (result.success) "✅" else "❌"} ${result.message}",
                                    color = if (result.success) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearActionResult() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Schließen", modifier = Modifier.size(16.dp))
                                }
                            }
                            LaunchedEffect(result) {
                                kotlinx.coroutines.delay(5000)
                                viewModel.clearActionResult()
                            }
                        }
                    }
                }
            }

            // Detektions-Historie
            if (detections.isNotEmpty()) {
                item {
                    Text(
                        "📋 Detektions-Historie (${detections.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(detections.take(20)) { detection ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    detection.sourceType.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                                        .format(detection.timestamp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text("${if (detection.rssi == 0) "📶 –" else "📶 ${detection.rssi} dBm"} · ${detection.nodeId ?: "–"}")
                            detection.latitude?.let { lat ->
                                detection.longitude?.let { lon ->
                                    Text("📍 ${"%.5f".format(lat)}, ${"%.5f".format(lon)}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            detection.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
