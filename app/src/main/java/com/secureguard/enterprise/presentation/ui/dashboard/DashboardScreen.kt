package com.secureguard.enterprise.presentation.ui.dashboard

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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.StatCard
import com.secureguard.enterprise.presentation.navigation.Routes

/**
 * Dashboard: alle Werte kommen live aus der Room-Datenbank bzw. von echten
 * Systemquellen (BatteryManager, Agent-Status) – keine festen Demo-Werte.
 * Oben: Einstiege zu Alarmen (mit Zähler), Agent-Konfiguration und
 * Einstellungen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ SecureGuard") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.ALERTS) }) {
                        BadgedBox(
                            badge = {
                                if (uiState.alertCount > 0) {
                                    Badge { Text(uiState.alertCount.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Alarme (${uiState.alertCount})"
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.AGENT_CONFIG) }) {
                        Icon(Icons.Default.SmartToy, contentDescription = "Agent konfigurieren")
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Batterie",
                value = if (uiState.batteryLevel >= 0) "${uiState.batteryLevel}%" else "–",
                icon = Icons.Default.BatteryFull,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Agent",
                value = if (uiState.agentRunning) "Aktiv" else "Gestoppt",
                icon = Icons.Default.PlayArrow,
                color = if (uiState.agentRunning) Color(0xFF1565C0) else Color(0xFFC62828),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Assets",
                value = uiState.totalAssets.toString(),
                icon = Icons.Default.LocationOn,
                color = Color(0xFF1565C0),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Online",
                value = uiState.onlineAssets.toString(),
                icon = Icons.Default.Sensors,
                color = Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Detektionen",
                value = uiState.detectionCount.toString(),
                icon = Icons.Default.Search,
                color = Color(0xFF6A1B9A),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Alarme",
                value = uiState.alertCount.toString(),
                icon = Icons.Default.Notifications,
                color = if (uiState.alertCount > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Letzter Sync: ${uiState.lastSyncTime} · " +
                "${uiState.offlineAssets} offline · " +
                "${uiState.maintenanceAssets} Wartung",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (assets.isEmpty()) {
            Text(
                text = "Noch keine Assets erfasst. Über „Assets“ → „+“ oder QR-Scan " +
                    "anlernen – die Datenbank wird nicht vorbefüllt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
        }
    }
}
