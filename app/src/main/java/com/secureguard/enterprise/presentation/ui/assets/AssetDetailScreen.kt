package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.ActionButton
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    val asset by viewModel.asset.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    LaunchedEffect(assetId) {
        viewModel.loadAsset(assetId)
    }

    var menuOpen by remember { mutableStateOf(false) }
    val a = asset

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a?.shortName ?: "Asset Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTelemetry() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menü")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Als Online markieren") },
                                onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.ONLINE) }
                            )
                            DropdownMenuItem(
                                text = { Text("Als Wartung markieren") },
                                onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.MAINTENANCE) }
                            )
                            DropdownMenuItem(
                                text = { Text("Als Offline markieren") },
                                onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.OFFLINE) }
                            )
                            if (a != null) {
                                DropdownMenuItem(
                                    text = { Text(if (a.externalAllowed) "Externe Quellen sperren" else "Externe Quellen erlauben") },
                                    onClick = { menuOpen = false; viewModel.setExternalAllowed(!a.externalAllowed) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Asset löschen") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.deleteAsset()
                                    navController.navigateUp()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (a == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status-Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (a.status) {
                                AssetStatus.ONLINE -> Color.Green.copy(alpha = 0.1f)
                                AssetStatus.MAINTENANCE -> Color(0xFFFFA000).copy(alpha = 0.1f)
                                AssetStatus.OFFLINE -> Color.Red.copy(alpha = 0.1f)
                                else -> Color.Gray.copy(alpha = 0.1f)
                            }
                        )
                    ) {
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
                                            .background(
                                                color = statusColor(a.status),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        a.status.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = statusColor(a.status)
                                    )
                                }
                                Text(
                                    "📶 ${a.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "📍 ${a.latitude ?: "Unbekannt"}, ${a.longitude ?: "Unbekannt"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "⏱ Letzte Aktualisierung: ${
                                    a.lastSeen?.let {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                    } ?: "Nie"
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Kartenvorschau
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "🗺️ Karte mit Position",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (a.latitude != null && a.longitude != null) {
                                    Text(
                                        "📍 ${a.latitude}, ${a.longitude}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Telemetrie
                item {
                    Text("📊 Telemetrie", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (telemetry == null) {
                                    "Noch keine Telemetrie gelesen. Tippe oben auf Aktualisieren (↻)."
                                } else {
                                    "Telemetrie zuletzt über BLE-GATT gelesen."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TelemetryItem("🔋 Batterie", telemetry?.battery?.let { "$it%" } ?: "–")
                                TelemetryItem("⛽ Kraftstoff", telemetry?.fuel?.let { "$it%" } ?: "–")
                                TelemetryItem("🔧 Motor", telemetry?.engineOk?.let { if (it) "OK" else "FEHLER" } ?: "–")
                                TelemetryItem("🛞 Reifen", "–")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TelemetryItem(
                                    "⏱ Betriebsstd.",
                                    telemetry?.operatingHours?.let { "%.1f h".format(it) } ?: "–"
                                )
                                TelemetryItem(
                                    "📏 Kilometer",
                                    telemetry?.distanceKm?.let { "%.0f km".format(it) } ?: "–"
                                )
                            }
                        }
                    }
                }

                // Aktionen
                item {
                    Text("🎯 Aktionen", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Warning,
                                    label = "🔔 Alarm",
                                    onClick = { viewModel.executeAction(ActionType.ALARM) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Lightbulb,
                                    label = "💡 Blinken",
                                    onClick = { viewModel.executeAction(ActionType.LIGHT) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.PowerSettingsNew,
                                    label = "🔇 Motor",
                                    onClick = { viewModel.executeAction(ActionType.MOTOR_OFF) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.BatteryAlert,
                                    label = "🔋 Batterie",
                                    onClick = { viewModel.executeAction(ActionType.BATTERY) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Message,
                                    label = "📝 Nachricht",
                                    onClick = { viewModel.executeAction(ActionType.MESSAGE) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    label = "📍 Position",
                                    onClick = { viewModel.executeAction(ActionType.POSITION) },
                                    enabled = a.status == AssetStatus.ONLINE
                                )
                            }
                            val res = actionResult
                            if (res != null && res != ActionResult.Processing) {
                                Text(
                                    text = if (res.success) "✅ ${res.message}" else "❌ ${res.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (res.success) Color.Green else Color.Red,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Weitere Suchoptionen
                item {
                    Text("🔍 Weitere Suchoptionen", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.startSearch() },
                                    enabled = !isSearching
                                ) {
                                    if (isSearching) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text("🔄 Suche starten")
                                    }
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.searchExternal() },
                                    enabled = a.externalAllowed && !isSearching
                                ) {
                                    Text("🌍 Extern")
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.searchSatellite() },
                                    enabled = !isSearching
                                ) {
                                    Text("📡 Satellit")
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.searchBluetooth() },
                                    enabled = !isSearching
                                ) {
                                    Text("📶 Bluetooth")
                                }
                                Text(
                                    text = "Einzelne Quelle antippen, um gezielt zu suchen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .align(Alignment.CenterVertically)
                                )
                            }
                            val sres = searchResult
                            if (sres != null) {
                                Text(
                                    text = if (sres.found) {
                                        "✅ Gefunden via ${sres.detection?.sourceType?.name} | " +
                                            "RSSI: ${sres.detection?.rssi} dBm"
                                    } else {
                                        "❌ Nicht gefunden"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Historie
                item {
                    Text("📋 Historien (${detections.size})", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (detections.isEmpty()) {
                                Text(
                                    "Keine Historieneinträge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                detections.take(10).forEach { detection ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            SimpleDateFormat("HH:mm", Locale.getDefault())
                                                .format(detection.timestamp),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "${detection.sourceType.name} | 📶 ${detection.rssi} dBm",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (detection.latitude != null) {
                                            Text(
                                                "📍 ${"%.4f".format(detection.latitude)}, ${
                                                    "%.4f".format(detection.longitude)
                                                }",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun statusColor(status: AssetStatus): Color = when (status) {
    AssetStatus.ONLINE -> Color.Green
    AssetStatus.MAINTENANCE -> Color(0xFFFFA000)
    AssetStatus.OFFLINE -> Color.Red
    else -> Color.Gray
}
