package com.secureguard.enterprise.presentation.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.AssetCard
import com.secureguard.enterprise.presentation.components.StatCard
import com.secureguard.enterprise.presentation.navigation.Routes
import com.secureguard.enterprise.presentation.ui.common.missingPermissions

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState()
    val agentRunning by viewModel.agentRunning.collectAsState()

    // Request all runtime permissions once on first launch.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result intentionally ignored; channels handle missing perms */ }
    LaunchedEffect(Unit) {
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛡️ SecureGuard Pro", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                    BadgedBox(badge = {
                        if (uiState.alertCount > 0) Badge { Text(uiState.alertCount.toString()) }
                    }) {
                        IconButton(onClick = { navController.navigate(Routes.ALERTS) }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alarme")
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.AGENT_CONFIG) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Agent-Konfiguration")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.toggleAgent() },
                containerColor = if (agentRunning) Color(0xFF2E7D32) else Color(0xFFC62828)
            ) {
                Icon(
                    if (agentRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
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
                                    color = if (agentRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (agentRunning) "AKTIV" else "INAKTIV",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (agentRunning) Color(0xFF2E7D32) else Color(0xFFC62828),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text("📶 ${uiState.onlineAssets}/${uiState.totalAssets}")
                    Text("🔋 ${uiState.batteryLevel}%")
                    Text("⏱ ${uiState.lastSyncTime}")
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.onlineAssets}/${uiState.totalAssets}",
                        label = "Assets",
                        icon = Icons.Default.Devices,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.activeSearches}",
                        label = "Suchen",
                        icon = Icons.Default.Search,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        value = "${uiState.alertCount}",
                        label = "Alarme",
                        icon = Icons.Default.Warning,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎯 Geschützte Assets", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { navController.navigate(Routes.ASSETS) }) {
                        Text("Alle anzeigen →")
                    }
                }
            }

            items(assets.take(5)) { asset ->
                AssetCard(
                    asset = asset,
                    onClick = { navController.navigate(Routes.assetDetail(asset.id)) }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate(Routes.ADD_ASSET) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("➕ Asset hinzufügen")
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(onClick = { navController.navigate(Routes.SCAN_QR) }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("QR-Scan")
                        }
                    }
                }
            }

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
