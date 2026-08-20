package com.secureguard.enterprise.presentation.ui.nodes

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.secureguard.enterprise.agent.NodeStatus

/**
 * Übersicht aller API-Abfrageknoten mit Status, Ratenlimit und
 * Ein/Aus-Schalter (verwaltet über den [ApiNodeManager]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatusScreen(
    navController: NavController,
    viewModel: NodeStatusViewModel = hiltViewModel()
) {
    val nodeStatus by viewModel.nodeStatus.collectAsState()
    val isQuerying by viewModel.isQuerying.collectAsState()
    val lastRefresh by viewModel.lastRefresh.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📡 Abfrageknoten") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.runFullQuery() }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test-Suche")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Letzte Aktualisierung: $lastRefresh" +
                    if (isQuerying) " · Abfrage läuft…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(nodeStatus.entries.sortedBy { it.key }) { (nodeId, status) ->
                    NodeStatusItem(
                        nodeId = nodeId,
                        status = status,
                        enabled = viewModel.isNodeEnabled(nodeId),
                        onToggle = { viewModel.toggleNode(nodeId) }
                    )
                }
            }
        }
    }
}

@Composable
fun NodeStatusItem(
    nodeId: String,
    status: NodeStatus,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            when (status) {
                                NodeStatus.ONLINE -> Color(0xFF4CAF50)
                                NodeStatus.OFFLINE -> Color(0xFFF44336)
                                NodeStatus.ERROR -> Color(0xFFFFC107)
                                NodeStatus.RATE_LIMITED -> Color(0xFFFF9800)
                                NodeStatus.UNKNOWN -> Color.Gray
                            },
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(nodeId, style = MaterialTheme.typography.titleSmall)
                    Text(
                        status.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = { onToggle() })
        }
    }
}
