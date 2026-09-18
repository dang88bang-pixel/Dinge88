package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgMetricTile
import com.secureguard.enterprise.presentation.designsystem.SgPrimaryButton
import com.secureguard.enterprise.presentation.designsystem.SgProgressBar
import com.secureguard.enterprise.presentation.designsystem.SgQuickTile
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader
import com.secureguard.enterprise.presentation.designsystem.SgSecondaryButton
import com.secureguard.enterprise.presentation.designsystem.SgSignalBars
import com.secureguard.enterprise.presentation.designsystem.SgSparkline
import com.secureguard.enterprise.presentation.designsystem.SgStatus
import com.secureguard.enterprise.presentation.designsystem.SgStatusBadge
import com.secureguard.enterprise.presentation.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Locale

/** 12 Navigationskacheln (§4). */
private val QUICK_TILES = listOf(
    Triple(Routes.ALERTS, "Alarme", Icons.Default.Notifications),
    Triple(Routes.ASSETS, "Assets", Icons.Default.LocationOn),
    Triple(Routes.MAP, "Karte", Icons.Default.Explore),
    Triple(Routes.NODE_STATUS, "Nodes", Icons.Default.Memory),
    Triple(Routes.TERMINAL, "Terminal", Icons.Default.Terminal),
    Triple(Routes.SENSOR_FUSION, "Sensor Fusion", Icons.Default.FactCheck),
    Triple(Routes.SECURITY, "Security", Icons.Default.Security),
    Triple(Routes.AGENT_CONFIG, "Agent", Icons.Default.SmartToy),
    Triple(Routes.ESP32_CONFIG, "ESP32", Icons.Default.BugReport),
    Triple(Routes.SLACK, "Slack", Icons.Default.FactCheck),
    Triple(Routes.TEMP_MAIL, "Temp Mail", Icons.Default.Mail),
    Triple(Routes.OPS_3D, "3D Ops", Icons.Default.ThreeDRotation)
)

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recentAlerts by viewModel.recentAlerts.collectAsState()
    val recentDetections by viewModel.recentDetections.collectAsState()
    val batteryLevel = getBatteryLevel(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Agent Hero
        item {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column {
                        androidx.compose.material3.Text(
                            "🛡️ SecureGuard",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        androidx.compose.material3.Text(
                            if (uiState.agentRunning) "Agent Online" else "Agent Offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.agentRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    SgStatusBadge(
                        status = if (uiState.agentRunning) SgStatus.HEALTHY else SgStatus.ALARM
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SgSecondaryButton(
                        text = if (uiState.agentRunning) "Agent stoppen" else "Agent starten",
                        icon = Icons.Default.PowerSettingsNew,
                        onClick = { viewModel.toggleAgent() },
                        modifier = Modifier.weight(1f)
                    )
                    SgPrimaryButton(
                        text = "Synchronisieren",
                        icon = Icons.Default.Bolt,
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
                    )
                }
                androidx.compose.material3.Text(
                    "Letzte Sync: ${uiState.lastSyncTime} · MQTT: ${if (uiState.mqttConnected) "verbunden" else "nicht verbunden"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Kennzahlen
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SgMetricTile(
                    value = uiState.totalAssets.toString(),
                    label = "Assets",
                    icon = Icons.Default.LocationOn,
                    status = SgStatus.INFO,
                    sparkline = uiState.onlineTrend,
                    modifier = Modifier.weight(1f)
                )
                SgMetricTile(
                    value = uiState.alertCount.toString(),
                    label = "Alarme",
                    icon = Icons.Default.Warning,
                    status = if (uiState.alertCount > 0) SgStatus.ALARM else SgStatus.HEALTHY,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SgMetricTile(
                    value = uiState.detectionCount.toString(),
                    label = "Detektionen",
                    icon = Icons.Default.Search,
                    status = SgStatus.INFO,
                    sparkline = uiState.detectionTrend,
                    modifier = Modifier.weight(1f)
                )
                SgMetricTile(
                    value = uiState.nodeCount.toString(),
                    label = "Nodes",
                    icon = Icons.Default.Memory,
                    status = SgStatus.INFO,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Flottenübersicht
        item {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SgSectionHeader(title = "Flottenübersicht")
                    FleetRow("Online", uiState.onlineAssets, uiState.totalAssets, SgStatus.HEALTHY)
                    FleetRow("Wartung", uiState.maintenanceAssets, uiState.totalAssets, SgStatus.WARNING)
                    FleetRow("Offline", uiState.offlineAssets, uiState.totalAssets, SgStatus.ALARM)
                    androidx.compose.material3.Text(
                        "Batterie: $batteryLevel%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Kanalaktivität (§4)
        item {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SgSectionHeader(title = "Kanalaktivität")
                    if (uiState.channelActivity.isEmpty()) {
                        androidx.compose.material3.Text(
                            "Noch keine Detektionsdaten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.channelActivity.take(6).forEach { ch ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Text(
                                    ch.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(0.34f)
                                )
                                SgProgressBar(
                                    progress = ch.count / (uiState.channelActivity.first().count.toFloat().coerceAtLeast(1f)),
                                    modifier = Modifier.weight(0.5f)
                                )
                                androidx.compose.material3.Text(
                                    "${ch.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(0.16f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Events (letzte Detektionen)
        item {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SgSectionHeader(title = "Live Events")
                    if (recentDetections.isEmpty()) {
                        androidx.compose.material3.Text(
                            "Noch keine Events.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentDetections.take(6).forEach { d ->
                            androidx.compose.material3.Text(
                                "${d.sourceType} · ${d.assetMac} · ${d.rssi} dBm · ${timeFmt(d.timestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Letzte Alarme
        item {
            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SgSectionHeader(
                        title = "Letzte Alarme",
                        actionLabel = "Alle",
                        onAction = { navController.navigate(Routes.ALERTS) }
                    )
                    if (recentAlerts.isEmpty()) {
                        androidx.compose.material3.Text(
                            "Keine Alarme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentAlerts.forEach { a ->
                            androidx.compose.material3.Text(
                                "⚠ ${a.message} · ${timeFmt(a.timestamp)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Schnellaktionen (echte Services)
        item {
            SgSectionHeader(title = "Schnellaktionen")
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SgSecondaryButton(
                    text = "Queue senden",
                    onClick = { viewModel.flushQueue() },
                    modifier = Modifier.weight(1f)
                )
                SgPrimaryButton(
                    text = if (uiState.agentRunning) "Suchzyklus" else "3D Ops",
                    icon = if (uiState.agentRunning) Icons.Default.Search else Icons.Default.ThreeDRotation,
                    onClick = {
                        if (uiState.agentRunning) viewModel.runCycle()
                        else navController.navigate(Routes.OPS_3D)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 12 Navigationskacheln
        item {
            SgSectionHeader(title = "Navigation")
        }
        items(QUICK_TILES.chunked(2)) { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (route, label, icon) ->
                    SgQuickTile(
                        title = label,
                        icon = icon,
                        onClick = { navController.navigate(route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FleetRow(label: String, count: Int, total: Int, status: SgStatus) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        SgStatusBadge(status = status)
        androidx.compose.material3.Text(
            "  $label",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.35f)
        )
        SgProgressBar(
            progress = if (total == 0) 0f else count / total.toFloat(),
            color = status.color,
            modifier = Modifier.weight(0.5f)
        )
        androidx.compose.material3.Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.15f)
        )
    }
}

private fun timeFmt(date: java.util.Date?): String {
    if (date == null) return "–"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
}

fun getBatteryLevel(context: Context): Int {
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100) / scale else 0
}
