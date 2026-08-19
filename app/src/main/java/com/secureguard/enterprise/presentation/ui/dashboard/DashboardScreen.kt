package com.secureguard.enterprise.presentation.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import com.secureguard.enterprise.presentation.components.AssetCard
import com.secureguard.enterprise.presentation.components.StatCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val assets by viewModel.assets.collectAsState()

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
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Notifications, contentDescription = "Alarme")
                        }
                    }
                }
            )
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "● ${if (uiState.agentRunning) "AKTIV" else "INAKTIV"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (uiState.agentRunning) Color.Green else Color.Red
                    )
                    Text(
                        text = "📶 ${uiState.onlineAssets} Assets",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "🔋 ${uiState.batteryLevel}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
            item {
                Text("🎯 Geschützte Assets", style = MaterialTheme.typography.titleMedium)
            }
            items(assets.take(5)) { asset ->
                AssetCard(asset = asset, onClick = {})
            }
        }
    }
}
