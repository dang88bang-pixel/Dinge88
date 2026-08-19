package com.secureguard.enterprise.presentation.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.AssetCard
import com.secureguard.enterprise.presentation.components.StatCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val agentRunning by viewModel.agentStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ SecureGuard Pro") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    BadgedBox(
                        badge = { Badge { Text(uiState.alertCount.toString()) } }
                    ) {
                        IconButton(onClick = { navController.navigate("alerts") }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Alarme")
                        }
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleAgent() },
                containerColor = if (agentRunning) Color(0xFF2E7D32) else Color(0xFFD32F2F)
            ) {
                Icon(
                    if (agentRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (agentRunning) "Agent stoppen" else "Agent starten"
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status-Header
            item {
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
                                    color = if (agentRunning) Color.Green else Color.Red,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (agentRunning) "AKTIV" else "INAKTIV",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (agentRunning) Color.Green else Color.Red
                        )
                    }
                    Text(
                        "📶 ${uiState.onlineAssets}/${uiState.totalAssets} Assets",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (uiState.batteryLevel >= 0) {
                            "🔋 ${uiState.batteryLevel}%"
                        } else {
                            "🔋 –"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("⏱ ${uiState.lastSyncTime}", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Statistik-Karten
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.onlineAssets}/${uiState.totalAssets}",
                        label = "Assets",
                        icon = Icons.Filled.Devices,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.activeSearches}",
                        label = "Suchen",
                        icon = Icons.Filled.Search,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.alertCount}",
                        label = "Alarme",
                        icon = Icons.Filled.Warning,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Asset-Liste
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎯 Geschützte Assets", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { navController.navigate("assets") }) {
                        Text("Alle anzeigen →")
                    }
                }
            }

            items(assets.take(5)) { asset ->
                AssetCard(
                    asset = asset,
                    onClick = { navController.navigate("asset_detail/${asset.id}") }
                )
            }

            // Asset hinzufügen
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate("add_asset") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("➕ Asset hinzufügen")
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { navController.navigate("scan") }) {
                            Text("📷 QR-Scan")
                        }
                    }
                }
            }

            // Agent-Status-Footer
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📊 Agent: ${if (agentRunning) "Aktiv" else "Inaktiv"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "🔄 Sync: ${uiState.lastSyncTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
