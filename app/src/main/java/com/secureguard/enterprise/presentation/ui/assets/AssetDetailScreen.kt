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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.ActionButton
import com.secureguard.enterprise.presentation.ui.common.ActionType
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
    val asset by viewModel.assetState.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(assetId) { viewModel.loadAsset(assetId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.shortName ?: "Asset Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshTelemetry() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menü")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Status: Online") },
                            onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.ONLINE) }
                        )
                        DropdownMenuItem(
                            text = { Text("Status: Offline") },
                            onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.OFFLINE) }
                        )
                        DropdownMenuItem(
                            text = { Text("Status: Wartung") },
                            onClick = { menuOpen = false; viewModel.setStatus(AssetStatus.MAINTENANCE) }
                        )
                        DropdownMenuItem(
                            text = { Text("Externe Suche umschalten") },
                            onClick = { menuOpen = false; viewModel.toggleExternalAllowed() }
                        )
                        DropdownMenuItem(
                            text = { Text("Asset löschen") },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (asset == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val current = asset!!
            val statusColor = when (current.status) {
                AssetStatus.ONLINE -> Color(0xFF2E7D32)
                AssetStatus.MAINTENANCE -> Color(0xFFF9A825)
                AssetStatus.OFFLINE -> Color(0xFFC62828)
                AssetStatus.SEARCHING -> Color(0xFF1565C0)
                AssetStatus.UNKNOWN -> Color.Gray
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = statusColor.copy(alpha = 0.1f)
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
                                            .background(statusColor, CircleShape)
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        current.status.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    "📍 ${current.latitude?.let { "%.4f".format(it) } ?: "?"}, " +
                                        "${current.longitude?.let { "%.4f".format(it) } ?: "?"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text("📶 ${current.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "⏱ Letzte Aktualisierung: ${
                                    current.lastSeen?.let {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                                    } ?: "Nie"
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        if (current.latitude != null && current.longitude != null) {
                            val context = LocalContext.current
                            val lat = current.latitude!!
                            val lon = current.longitude!!
                            val mapView = remember(current.id) {
                                MapView(context).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    setBuiltInZoomControls(false)
                                    controller.setZoom(16.0)
                                    controller.setCenter(GeoPoint(lat, lon))
                                }
                            }
                            DisposableEffect(current.id) {
                                mapView.onResume()
                                onDispose { mapView.onPause() }
                            }
                            AndroidView(
                                factory = { mapView },
                                update = { view ->
                                    view.overlays.clear()
                                    val marker = Marker(view).apply {
                                        position = GeoPoint(lat, lon)
                                        title = current.shortName
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    }
                                    view.overlays.add(marker)
                                    view.controller.setCenter(GeoPoint(lat, lon))
                                    view.invalidate()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Keine Position bekannt",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Text("📊 Telemetrie", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TelemetryItem(
                                    "🔋 Batterie",
                                    telemetry?.batteryPercent?.let { "$it%" }
                                        ?: (current.batteryLevel?.let { "$it%" } ?: "–")
                                )
                                TelemetryItem(
                                    "⛽ Kraftstoff",
                                    telemetry?.fuelPercent?.let { "$it%" } ?: "–"
                                )
                                TelemetryItem(
                                    "🔧 Motor",
                                    if (telemetry?.motorOk != false) "OK" else "FEHLER"
                                )
                                TelemetryItem(
                                    "🛞 Reifen",
                                    if (telemetry?.tiresOk != false) "OK" else "FEHLER"
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TelemetryItem(
                                    "⏱ Betriebsstd.",
                                    telemetry?.operatingHours?.let { "%,.1f h".format(it) } ?: "–"
                                )
                                TelemetryItem(
                                    "📏 Kilometer",
                                    telemetry?.kilometers?.let { "%,.1f km".format(it) } ?: "–"
                                )
                            }
                        }
                    }
                }

                item {
                    Text("🎯 Aktionen", style = MaterialTheme.typography.titleMedium)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val online = current.status == AssetStatus.ONLINE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.Warning, "🔔 Alarm",
                                    { viewModel.executeAction(ActionType.ALARM) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.Lightbulb, "💡 Blinken",
                                    { viewModel.executeAction(ActionType.LIGHT) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.PowerSettingsNew, "🔇 Motor",
                                    { viewModel.executeAction(ActionType.MOTOR_OFF) }, online)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.BatteryAlert, "🔋 Batterie",
                                    { viewModel.executeAction(ActionType.BATTERY) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.Message, "📝 Nachricht",
                                    { viewModel.executeAction(ActionType.MESSAGE) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.LocationOn, "📍 Position",
                                    { viewModel.executeAction(ActionType.POSITION) }, online)
                            }
                            if (actionResult != null && actionResult != com.secureguard.enterprise.presentation.ui.common.ActionResult.Processing) {
                                Text(
                                    text = if (actionResult!!.success) "✅ ${actionResult!!.message}"
                                    else "❌ ${actionResult!!.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (actionResult!!.success) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }

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
                                    } else Text("🔄 Suche")
                                }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.startExternalSearch() },
                                    enabled = current.externalAllowed && !isSearching
                                ) { Text("🌍 Extern") }
                                Button(
                                    modifier = Modifier.weight(1f),
                                    onClick = { viewModel.startSatelliteSearch() },
                                    enabled = !isSearching
                                ) { Text("📡 Satellit") }
                            }
                            searchResult?.let { result ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (result.found)
                                        "✅ Gefunden! RSSI: ${result.detection?.rssi} dBm"
                                    else "❌ Nicht gefunden",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                item {
                    Text("📋 Historie (${detections.size})", style = MaterialTheme.typography.titleMedium)
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
                                                "📍 ${"%.4f".format(detection.latitude)}, " +
                                                    "${"%.4f".format(detection.longitude)}",
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Asset löschen?") },
            text = { Text("„${asset?.shortName ?: assetId}“ wird unwiderruflich entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAsset { navController.navigateUp() }
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelemetryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
