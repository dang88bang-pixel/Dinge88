package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.util.formatDateTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    assetId: String,
    viewModel: AssetViewModel = hiltViewModel()
) {
    LaunchedEffect(assetId) { viewModel.selectAsset(assetId) }

    val asset by viewModel.selectedAsset.collectAsState()
    val detections by viewModel.detections.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.shortName ?: "Asset") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        val a = asset
        if (a == null) {
            Text(
                text = "Asset nicht gefunden.",
                modifier = Modifier.padding(paddingValues).padding(24.dp)
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(a.name, style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Status: ${a.status.name}", style = MaterialTheme.typography.bodyMedium)
                        Text("MAC: ${a.mac}", style = MaterialTheme.typography.bodyMedium)
                        Text("Position: ${a.latitude ?: "–"}, ${a.longitude ?: "–"}", style = MaterialTheme.typography.bodyMedium)
                        a.lastSeen?.let {
                            Text("Zuletzt gesehen: ${it.formatDateTime()}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.setStatus(a.id, AssetStatus.ONLINE) }) {
                                Text("Online")
                            }
                            OutlinedButton(onClick = { viewModel.setStatus(a.id, AssetStatus.MAINTENANCE) }) {
                                Text("Wartung")
                            }
                            OutlinedButton(onClick = { viewModel.setStatus(a.id, AssetStatus.OFFLINE) }) {
                                Text("Offline")
                            }
                        }
                    }
                }
            }
            item { Text("Detections", style = MaterialTheme.typography.titleMedium) }
            if (detections.isEmpty()) {
                item { Text("Keine Detections vorhanden.") }
            } else {
                items(detections.size) { index ->
                    val d = detections[index]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Quelle: ${d.sourceType.name}", style = MaterialTheme.typography.bodyMedium)
                            Text("RSSI: ${d.rssi} dBm | ${d.timestamp.formatDateTime()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
