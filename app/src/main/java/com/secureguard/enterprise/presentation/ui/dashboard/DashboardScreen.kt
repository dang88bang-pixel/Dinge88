package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.presentation.designsystem.Sg
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgMeter
import com.secureguard.enterprise.presentation.designsystem.SgMetricTile
import com.secureguard.enterprise.presentation.designsystem.SgPill
import com.secureguard.enterprise.presentation.designsystem.SgQuickTile
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader
import com.secureguard.enterprise.presentation.designsystem.SgStatusDot
import com.secureguard.enterprise.presentation.designsystem.compactDuration
import com.secureguard.enterprise.presentation.designsystem.relativeTime
import com.secureguard.enterprise.presentation.designsystem.severityColor
import com.secureguard.enterprise.presentation.designsystem.severityLabel
import com.secureguard.enterprise.presentation.designsystem.sourceColor
import com.secureguard.enterprise.presentation.designsystem.sourceIcon
import com.secureguard.enterprise.presentation.designsystem.sourceLabel
import com.secureguard.enterprise.presentation.designsystem.statusColor
import com.secureguard.enterprise.presentation.navigation.Routes

/**
 * Lage-Dashboard.
 *
 * Beantwortet in einem Blick: Läuft der Agent? Wie viele Werte sind
 * geschützt? Welche Kanäle liefern gerade? Was verlangt sofortige
 * Aufmerksamkeit? Und: von hier führt genau ein Tipp in jedes Werkzeug.
 */
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val batteryLevel = remember { getBatteryLevel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val detections by viewModel.detections.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val agentStatus by viewModel.agentStatus.collectAsState()

    val series = remember(detections) { detectionSeries(detections) }
    val channelActivity = remember(detections) { channelActivity(detections) }
    val openAlerts = remember(alerts) { alerts.filter { !it.acknowledged } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Sg.Space.lg),
        verticalArrangement = Arrangement.spacedBy(Sg.Space.md)
    ) {
        item {
            AgentHero(
                running = uiState.agentRunning,
                uptimeMillis = agentStatus.uptimeMillis,
                cycle = agentStatus.cycle,
                intervalSec = agentStatus.settings.interval,
                lastSync = uiState.lastSyncTime,
                onToggle = { viewModel.toggleAgent() },
                onRefresh = { viewModel.refresh() },
                onOps3d = { navController.navigate(Routes.OPS_3D) }
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgMetricTile(
                    modifier = Modifier.weight(1f),
                    value = uiState.totalAssets.toString(),
                    label = "Geschützte Assets",
                    icon = Icons.Default.Inventory2,
                    color = MaterialTheme.colorScheme.primary,
                    trend = "${uiState.maintenanceAssets} Wartung",
                    onClick = { navController.navigate(Routes.ASSETS) }
                )
                SgMetricTile(
                    modifier = Modifier.weight(1f),
                    value = uiState.onlineAssets.toString(),
                    label = "Online erreichbar",
                    icon = Icons.Default.Bolt,
                    color = statusColor(AssetStatus.ONLINE),
                    trend = "${uiState.offlineAssets} offline",
                    trendUp = uiState.offlineAssets == 0,
                    series = series
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgMetricTile(
                    modifier = Modifier.weight(1f),
                    value = uiState.detectionCount.toString(),
                    label = "Detektionen gesamt",
                    icon = Icons.Default.Radar,
                    color = sourceColor(DetectionSource.LORA),
                    trend = "${detections.size} im Cache",
                    series = series
                )
                SgMetricTile(
                    modifier = Modifier.weight(1f),
                    value = openAlerts.size.toString(),
                    label = "Offene Alarme",
                    icon = Icons.Default.Warning,
                    color = statusColor(AssetStatus.OFFLINE),
                    trend = "${alerts.size} gesamt",
                    trendUp = openAlerts.isEmpty(),
                    onClick = { navController.navigate(Routes.ALERTS) }
                )
            }
        }

        item {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                SgSectionHeader(
                    title = "Kanal-Aktivität",
                    subtitle = "Treffer je Detection-Kanal im lokalen Verlauf",
                    icon = Icons.Default.Hub,
                    trailing = {
                        TextButton(onClick = { navController.navigate(Routes.SENSOR_FUSION) }) {
                            Text("Fusion")
                        }
                    }
                )
                Spacer(Modifier.height(Sg.Space.md))
                if (channelActivity.isEmpty()) {
                    Text(
                        "Noch keine Detektionen erfasst. Starte den Agenten oder löse einen Suchlauf im 3D-Lagebild aus.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val maxHits = channelActivity.maxOf { it.second }
                    channelActivity.forEach { (source, hits) ->
                        ChannelRow(source = source, hits = hits, maxHits = maxHits)
                        Spacer(Modifier.height(Sg.Space.sm))
                    }
                }
            }
        }

        item {
            SgSectionHeader(
                title = "Alarme",
                subtitle = if (openAlerts.isEmpty()) "Keine offenen Meldungen"
                else "${openAlerts.size} verlangen Aufmerksamkeit",
                icon = Icons.Default.NotificationsActive,
                accent = statusColor(AssetStatus.OFFLINE),
                trailing = {
                    if (openAlerts.isNotEmpty()) {
                        IconButton(onClick = { viewModel.acknowledgeAllAlerts() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Alle bestätigen")
                        }
                    }
                }
            )
        }

        if (openAlerts.isEmpty()) {
            item {
                SgCard(modifier = Modifier.fillMaxWidth(), accent = statusColor(AssetStatus.ONLINE)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SgStatusDot(color = statusColor(AssetStatus.ONLINE), live = uiState.agentRunning)
                        Spacer(Modifier.width(Sg.Space.sm))
                        Text("Lage unauffällig – alle Meldungen bestätigt.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(openAlerts.take(4), key = { it.id }) { alert ->
                AlertRow(alert = alert, onAck = { viewModel.acknowledgeAlert(alert.id) })
            }
            if (openAlerts.size > 4) {
                item {
                    TextButton(onClick = { navController.navigate(Routes.ALERTS) }) {
                        Text("Alle ${openAlerts.size} Alarme anzeigen")
                    }
                }
            }
        }

        item {
            SgSectionHeader(
                title = "Werkzeuge",
                subtitle = "Alle Betriebsfunktionen – ein Tipp entfernt",
                icon = Icons.Default.Settings
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ThreeDRotation,
                    title = "3D-Lagebild",
                    subtitle = "Operations Center",
                    color = MaterialTheme.colorScheme.primary,
                    badge = "NEU",
                    onClick = { navController.navigate(Routes.OPS_3D) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Bolt,
                    title = "Aktionen",
                    subtitle = "Befehle & Sammelbefehle",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = { navController.navigate(Routes.ACTIONS) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Map,
                    title = "Karte",
                    subtitle = "Positionen & Verlauf",
                    color = sourceColor(DetectionSource.WIFI),
                    onClick = { navController.navigate(Routes.MAP) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Explore,
                    title = "Sensor-Fusion",
                    subtitle = "Kanäle kombinieren",
                    color = sourceColor(DetectionSource.CROWD),
                    onClick = { navController.navigate(Routes.SENSOR_FUSION) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Security,
                    title = "Sicherheit",
                    subtitle = "Rollen, PIN, Audit",
                    color = statusColor(AssetStatus.OFFLINE),
                    onClick = { navController.navigate(Routes.SECURITY) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SmartToy,
                    title = "Agent",
                    subtitle = "Intervall & Lernmodus",
                    color = sourceColor(DetectionSource.API),
                    onClick = { navController.navigate(Routes.AGENT_CONFIG) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Memory,
                    title = "ESP32",
                    subtitle = "Gateways & Firmware",
                    color = sourceColor(DetectionSource.URBAN),
                    onClick = { navController.navigate(Routes.ESP32_CONFIG) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Hub,
                    title = "Knoten",
                    subtitle = "API- & Netzstatus",
                    color = sourceColor(DetectionSource.MQTT),
                    onClick = { navController.navigate(Routes.NODE_STATUS) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Terminal,
                    title = "Terminal",
                    subtitle = "Diagnose-Konsole",
                    color = sourceColor(DetectionSource.TELEMETRY),
                    onClick = { navController.navigate(Routes.TERMINAL) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HealthAndSafety,
                    title = "Systemzustand",
                    subtitle = "Health & Wartung",
                    color = sourceColor(DetectionSource.SATELLITE),
                    onClick = { navController.navigate(Routes.HEALTH) }
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Mail,
                    title = "Temp-Mail",
                    subtitle = "Registrierungen",
                    color = sourceColor(DetectionSource.NFC),
                    onClick = { navController.navigate(Routes.TEMP_MAIL) }
                )
                SgQuickTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.BatteryFull,
                    title = "Gerät",
                    subtitle = "Akku $batteryLevel% · Einstellungen",
                    color = statusColor(AssetStatus.MAINTENANCE),
                    onClick = { navController.navigate(Routes.SETTINGS) }
                )
            }
        }

        item { Spacer(Modifier.height(Sg.Space.xxl)) }
    }
}

/* ------------------------------------------------------------------ */
/* Bausteine                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun AgentHero(
    running: Boolean,
    uptimeMillis: Long,
    cycle: Long,
    intervalSec: Int,
    lastSync: String,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onOps3d: () -> Unit
) {
    val accent = if (running) statusColor(AssetStatus.ONLINE) else statusColor(AssetStatus.OFFLINE)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sg.Radius.xl))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        accent.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .padding(Sg.Space.lg)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SgStatusDot(color = accent, live = running, size = 12.dp)
                Spacer(Modifier.width(Sg.Space.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (running) "Agent aktiv" else "Agent gestoppt",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (running) "9 Detection-Kanäle + 3 Echtzeit-Kanäle laufen parallel"
                        else "Keine automatische Suche – Assets werden nicht überwacht",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                }
            }

            Spacer(Modifier.height(Sg.Space.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
            ) {
                SgPill(text = "Laufzeit ${compactDuration(uptimeMillis)}", color = accent, live = running)
                SgPill(text = "Zyklus $cycle", color = MaterialTheme.colorScheme.primary)
                SgPill(text = "Intervall ${intervalSec}s", color = MaterialTheme.colorScheme.tertiary)
                SgPill(text = "Sync $lastSync", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(Sg.Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Sg.Radius.md),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Icon(
                        if (running) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Sg.Space.sm))
                    Text(if (running) "Agent stoppen" else "Agent starten")
                }
                OutlinedButton(
                    onClick = onOps3d,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(Sg.Radius.md)
                ) {
                    Icon(Icons.Default.ThreeDRotation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Sg.Space.sm))
                    Text("3D-Lagebild")
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(source: DetectionSource, hits: Int, maxHits: Int) {
    val color = sourceColor(source)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(sourceIcon(source), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Sg.Space.sm))
        Text(
            sourceLabel(source),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(88.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SgMeter(
            progress = if (maxHits == 0) 0f else hits.toFloat() / maxHits,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Sg.Space.sm))
        Text(
            hits.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AlertRow(alert: Alert, onAck: () -> Unit) {
    val color = severityColor(alert.severity)
    SgCard(
        modifier = Modifier.fillMaxWidth(),
        accent = color,
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Sg.Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)) {
                    SgPill(text = severityLabel(alert.severity), color = color)
                    Text(
                        relativeTime(alert.timestamp.time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onAck) { Text("OK") }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Ableitungen                                                         */
/* ------------------------------------------------------------------ */

/** Detektionen der letzten 5 Minuten in 20 Zeit-Buckets – Datenbasis der Sparklines. */
private fun detectionSeries(detections: List<Detection>, buckets: Int = 20): List<Float> {
    if (detections.isEmpty()) return emptyList()
    val now = System.currentTimeMillis()
    val bucketMs = 15_000L
    val out = FloatArray(buckets)
    detections.forEach { detection ->
        val index = buckets - 1 - ((now - detection.timestamp.time) / bucketMs).toInt()
        if (index in 0 until buckets) out[index] = out[index] + 1f
    }
    return out.toList()
}

/** Trefferzahl je Kanal, absteigend sortiert. */
private fun channelActivity(detections: List<Detection>): List<Pair<DetectionSource, Int>> =
    detections.groupingBy { it.sourceType }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(8)

fun getBatteryLevel(context: Context): Int {
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100) / scale else 0
}
