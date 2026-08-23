package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.ActionButton
import com.secureguard.enterprise.presentation.ui.common.ActionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asset-Detail: echte Telemetrie (GATT), Mehrkanal-Suche (alle Kanäle oder
 * einzeln), Detektions-Historie aus der Room-DB, Aktionen über die volle
 * Kanalkette sowie Verwaltungs-Funktionen (Wartung, Extern-Freigabe, Löschen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    val asset by viewModel.assetState.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val assetDeleted by viewModel.assetDeleted.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd.MM. HH:mm:ss", Locale.getDefault()) }

    // Asset laden (einmalig pro assetId)
    LaunchedEffect(assetId) { viewModel.loadAsset(assetId) }

    // Nach Löschung zurück zur Liste navigieren
    LaunchedEffect(assetDeleted) {
        if (assetDeleted) navController.navigateUp()
    }

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
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Build, contentDescription = "Verwaltung")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (asset?.maintenanceDue == true) "Wartung abschließen"
                                    else "In Wartung setzen"
                                )
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.setMaintenance(asset?.maintenanceDue != true)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (asset?.externalAllowed == true) "Externe Quellen sperren"
                                    else "Externe Quellen erlauben"
                                )
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.setExternalAllowed(asset?.externalAllowed != true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Suchen-Status zurücksetzen") },
                            onClick = {
                                menuOpen = false
                                viewModel.resetSearchStatus()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Asset löschen", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            }
                        )
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
            // ---------- Kopf ----------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(asset?.name ?: "–", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "MAC: ${asset?.mac ?: "–"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Status: ${asset?.status?.name ?: "–"} · RSSI ${asset?.rssi ?: "–"} dBm",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Zuletzt gesehen: ${asset?.lastSeen?.let { dateFormat.format(it) } ?: "unbekannt"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (asset?.externalAllowed == true) {
                            Text(
                                "🌐 Externe Quellen freigegeben",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // ---------- Suche ----------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔍 Suche", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.startSearch() },
                                enabled = !isSearching,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (isSearching) "Suche läuft…" else "Alle Kanäle") }
                            OutlinedButton(
                                onClick = { viewModel.startExternalSearch() },
                                enabled = !isSearching,
                                modifier = Modifier.weight(1f)
                            ) { Text("Extern") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.startSatelliteSearch() },
                                enabled = !isSearching,
                                modifier = Modifier.weight(1f)
                            ) { Text("Satellit") }
                        }
                        searchResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            if (result.found && result.detection != null) {
                                Text(
                                    "✅ Gefunden via ${result.detection.sourceType.name} " +
                                        "(RSSI ${result.detection.rssi} dBm)",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                result.detection.latitude?.let { lat ->
                                    Text(
                                        "📍 ${"%.5f".format(lat)} / " +
                                            "${"%.5f".format(result.detection.longitude ?: 0.0)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                Text(
                                    "❌ Nicht gefunden (kein Kanal hatte einen echten Treffer)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Telemetrie ----------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📊 Telemetrie (GATT)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        telemetry?.let { t ->
                            Text("🔋 Batterie: ${t.batteryPercent ?: "–"} %")
                            Text("⛽ Kraftstoff: ${t.fuelPercent ?: "–"} %")
                            Text("🏃 Motor OK: ${if (t.motorOk) "ja" else "nein"}")
                            Text("🛞 Reifen OK: ${if (t.tiresOk) "ja" else "nein"}")
                            Text("⏱️ Betriebsstunden: ${t.operatingHours ?: "–"} h")
                            Text("🛣️ Kilometer: ${t.kilometers ?: "–"} km")
                            Text(
                                "Stand: ${dateFormat.format(t.timestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } ?: Text(
                            "Kein Gerät verbunden – Telemetrie erst nach echtem GATT-Read verfügbar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- Aktionen ----------
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚡ Fernaktionen", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ActionButton(
                                Modifier.weight(1f), Icons.Default.Warning, "Alarm",
                                { viewModel.executeAction(ActionType.ALARM) }
                            )
                            ActionButton(
                                Modifier.weight(1f), Icons.Default.Public, "Position",
                                { viewModel.executeAction(ActionType.POSITION) }
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ActionButton(
                                Modifier.weight(1f), Icons.Default.Refresh, "Telemetrie",
                                { viewModel.executeAction(ActionType.TELEMETRY) }
                            )
                            ActionButton(
                                Modifier.weight(1f), Icons.Default.Delete, "Motor aus",
                                { viewModel.executeAction(ActionType.MOTOR_OFF) }
                            )
                        }
                        actionResult?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (if (result.success) "✅ " else "❌ ") + result.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // ---------- Historie ----------
            item {
                Text("🕘 Detektions-Historie (${detections.size})", fontWeight = FontWeight.SemiBold)
            }
            items(detections.take(50)) { detection ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "${detection.sourceType.name} · ${detection.rssi} dBm",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${dateFormat.format(detection.timestamp)} · ${detection.nodeId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Asset löschen?") },
            text = { Text("„${asset?.name ?: ""}" wird aus der Whitelist entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAsset()
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}
