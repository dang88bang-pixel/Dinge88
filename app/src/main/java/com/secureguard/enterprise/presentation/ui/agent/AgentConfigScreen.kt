package com.secureguard.enterprise.presentation.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    navController: NavController,
    viewModel: AgentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🤖 Agent Konfiguration") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSettings() }) {
                        Icon(Icons.Filled.Save, contentDescription = "Speichern")
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
            // Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.agentRunning) {
                            Color.Green.copy(alpha = 0.1f)
                        } else {
                            Color.Red.copy(alpha = 0.1f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (uiState.agentRunning) Color.Green else Color.Red,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (uiState.agentRunning) "AKTIV" else "INAKTIV",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (uiState.agentRunning) Color.Green else Color.Red
                                )
                            }
                            Text(
                                "⏱ Laufzeit: ${uiState.runtime}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "📊 Gesamtdauer: ${uiState.progress}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LinearProgressIndicator(
                            progress = { uiState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startAgent() },
                                enabled = !uiState.agentRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("▶️ Start")
                            }
                            Button(
                                onClick = { viewModel.stopAgent() },
                                enabled = uiState.agentRunning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⏹️ Stop")
                            }
                        }
                    }
                }
            }

            // Dauer
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📅 Gesamtdauer", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.duration == "1h",
                                onClick = { viewModel.setDuration("1h") },
                                label = { Text("1 Std.") }
                            )
                            FilterChip(
                                selected = uiState.duration == "6h",
                                onClick = { viewModel.setDuration("6h") },
                                label = { Text("6 Std.") }
                            )
                            FilterChip(
                                selected = uiState.duration == "24h",
                                onClick = { viewModel.setDuration("24h") },
                                label = { Text("24 Std.") }
                            )
                            FilterChip(
                                selected = uiState.duration == "1w",
                                onClick = { viewModel.setDuration("1w") },
                                label = { Text("1 Woche") }
                            )
                            FilterChip(
                                selected = uiState.duration == "unlimited",
                                onClick = { viewModel.setDuration("unlimited") },
                                label = { Text("∞") }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.customDays.toString(),
                                onValueChange = { viewModel.setCustomDays(it.toIntOrNull() ?: 0) },
                                label = { Text("Tage") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.applyCustomDuration() },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text("✔️ Speichern")
                            }
                        }
                    }
                }
            }

            // Intervall
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⏱ Abfrageintervall", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.interval == 30,
                                onClick = { viewModel.setInterval(30) },
                                label = { Text("30 Sek.") }
                            )
                            FilterChip(
                                selected = uiState.interval == 60,
                                onClick = { viewModel.setInterval(60) },
                                label = { Text("1 Min.") }
                            )
                            FilterChip(
                                selected = uiState.interval == 300,
                                onClick = { viewModel.setInterval(300) },
                                label = { Text("5 Min.") }
                            )
                            FilterChip(
                                selected = uiState.interval == 900,
                                onClick = { viewModel.setInterval(900) },
                                label = { Text("15 Min.") }
                            )
                            FilterChip(
                                selected = uiState.interval == 3600,
                                onClick = { viewModel.setInterval(3600) },
                                label = { Text("1 Std.") }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uiState.customInterval.toString(),
                                onValueChange = { viewModel.setCustomInterval(it.toIntOrNull() ?: 30) },
                                label = { Text("Sekunden") },
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.applyCustomInterval() },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text("✔️ Speichern")
                            }
                        }
                    }
                }
            }

            // Priorisierung
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎯 Priorisierung", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = uiState.priority == "high",
                                onClick = { viewModel.setPriority("high") },
                                label = { Text("Hoch") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Red.copy(alpha = 0.2f)
                                )
                            )
                            FilterChip(
                                selected = uiState.priority == "medium",
                                onClick = { viewModel.setPriority("medium") },
                                label = { Text("Mittel") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFA000).copy(alpha = 0.2f)
                                )
                            )
                            FilterChip(
                                selected = uiState.priority == "low",
                                onClick = { viewModel.setPriority("low") },
                                label = { Text("Niedrig") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color.Green.copy(alpha = 0.2f)
                                )
                            )
                        }
                        CheckRow(
                            checked = uiState.dynamicPriority,
                            onCheckedChange = { viewModel.setDynamicPriority(it) },
                            label = "⚡ Dynamische Anpassung",
                            topPadding = 8
                        )
                        CheckRow(
                            checked = uiState.learningMode,
                            onCheckedChange = { viewModel.setLearningMode(it) },
                            label = "🔄 Lernmodus (rekursive Verbesserung)",
                            topPadding = 4
                        )
                    }
                }
            }

            // Speichern-Button
            item {
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("💾 Konfiguration speichern")
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    topPadding: Int
) {
    Row(
        modifier = Modifier.padding(top = topPadding.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
