package com.secureguard.enterprise.presentation.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    navController: NavController,
    viewModel: HealthViewModel = hiltViewModel()
) {
    val health by viewModel.health.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System-Health") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("health_refresh")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .testTag("health_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (health?.allCriticalOk == true) "✅ Kritische Systeme OK"
                            else if (loading) "⏳ Prüfe …"
                            else "⚠ Status prüfen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        val ts = health?.checkedAt?.let {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "–"
                        Text("Stand: $ts", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Assets ${health?.assetCount ?: 0} · Detektionen ${health?.detectionCount ?: 0} · " +
                                "Offene Alerts ${health?.openAlerts ?: 0}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Agent: " + if (health?.agentRunning == true) {
                                "läuft (Zyklus ${health?.agentCycle})"
                            } else {
                                "gestoppt"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            items(health?.components.orEmpty()) { c ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                c.label,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                c.detail,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (c.ok) "✅" else "❌")
                    }
                }
            }
        }
    }
}
