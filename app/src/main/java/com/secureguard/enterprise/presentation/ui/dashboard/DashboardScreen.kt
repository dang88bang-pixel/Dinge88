package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.StatCard
import com.secureguard.enterprise.presentation.navigation.Routes

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "🛡️ SecureGuard Dashboard",
            style = MaterialTheme.typography.headlineSmall
        )

        // Navigation zu Settings + Agent-Konfiguration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (uiState.agentRunning) "🟢 Agent aktiv" else "🔴 Agent inaktiv",
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                IconButton(onClick = { navController.navigate(Routes.AGENT_CONFIG) }) {
                    Icon(Icons.Default.SmartToy, contentDescription = "Agent-Konfiguration")
                }
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "$batteryLevel%",
                label = "Batterie",
                icon = Icons.Default.BatteryFull,
                color = Color(0xFF2E7D32)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.totalAssets.toString(),
                label = "Assets",
                icon = Icons.Default.LocationOn,
                color = Color(0xFF1565C0)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = detections.size.toString(),
                label = "Detektionen",
                icon = Icons.Default.Search,
                color = Color(0xFF6A1B9A)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.alertCount.toString(),
                label = "Alarme",
                icon = Icons.Default.Warning,
                color = Color(0xFFC62828)
            )
        }

        // Status-Übersicht
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("🟢 ${uiState.onlineAssets}", style = MaterialTheme.typography.bodyMedium)
            Text("🔴 ${uiState.offlineAssets}", style = MaterialTheme.typography.bodyMedium)
            Text("🟡 ${uiState.maintenanceAssets}", style = MaterialTheme.typography.bodyMedium)
        }

        // Agent-Status
        Text(
            if (uiState.agentRunning) "🤖 Agent: Läuft" else "🤖 Agent: Gestoppt",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Letzte Sync: ${uiState.lastSyncTime}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.toggleAgent() }) {
                Text(if (uiState.agentRunning) "Agent stoppen" else "Agent starten")
            }
            Button(onClick = { navController.navigate(Routes.ALERTS) }) {
                Text("Alarme (${uiState.alertCount})")
            }
            Button(onClick = { viewModel.refresh() }) {
                Text("🔄")
            }
        }
    }
}

fun getBatteryLevel(context: Context): Int {
    val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    val batteryStatus = context.registerReceiver(null, ifilter)
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100) / scale else 0
}
