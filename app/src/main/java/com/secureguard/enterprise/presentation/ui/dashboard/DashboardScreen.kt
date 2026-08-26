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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.R
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
            stringResource(R.string.title_dashboard),
            style = MaterialTheme.typography.headlineSmall
        )

        // Navigation zu Settings + Agent-Konfiguration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(if (uiState.agentRunning) R.string.agent_state_active else R.string.agent_state_inactive),
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                IconButton(onClick = { navController.navigate(Routes.TERMINAL) }) {
                    Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.nav_terminal))
                }
                IconButton(onClick = { navController.navigate(Routes.SENSOR_FUSION) }) {
                    Icon(Icons.Default.Explore, contentDescription = stringResource(R.string.nav_sensor_fusion))
                }
                IconButton(onClick = { navController.navigate(Routes.SECURITY) }) {
                    Icon(Icons.Default.Security, contentDescription = stringResource(R.string.nav_security))
                }
                IconButton(onClick = { navController.navigate(Routes.ESP32_CONFIG) }) {
                    Icon(Icons.Default.Memory, contentDescription = stringResource(R.string.nav_esp32_config))
                }
                IconButton(onClick = { navController.navigate(Routes.AGENT_CONFIG) }) {
                    Icon(Icons.Default.SmartToy, contentDescription = stringResource(R.string.title_agent_config))
                }
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
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
                label = stringResource(R.string.label_battery),
                icon = Icons.Default.BatteryFull,
                color = Color(0xFF2E7D32)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.totalAssets.toString(),
                label = stringResource(R.string.label_assets),
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
                value = uiState.detectionCount.toString(),
                label = stringResource(R.string.label_detections),
                icon = Icons.Default.Search,
                color = Color(0xFF6A1B9A)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                value = uiState.alertCount.toString(),
                label = stringResource(R.string.label_alerts),
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
            stringResource(if (uiState.agentRunning) R.string.agent_state_running else R.string.agent_state_stopped),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.last_sync, uiState.lastSyncTime),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.toggleAgent() }) {
                Text(stringResource(if (uiState.agentRunning) R.string.btn_stop_agent else R.string.btn_start_agent))
            }
            Button(onClick = { navController.navigate(Routes.ALERTS) }) {
                Text(stringResource(R.string.btn_alerts, uiState.alertCount))
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
