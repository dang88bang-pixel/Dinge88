package com.secureguard.enterprise.presentation.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.ExperimentalMaterial3Api

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
                title = { Text("🤖 Agent-Konfiguration") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSettings() }) {
                        Icon(Icons.Default.Save, contentDescription = "Speichern")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { StatusCard(uiState, onToggle = { viewModel.toggleAgent() }) }
            item { DurationCard(uiState, viewModel) }
            item { IntervalCard(uiState, viewModel) }
            item { PriorityCard(uiState, viewModel) }
            item {
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text("💾 Konfiguration speichern") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCard(uiState: AgentUiState, onToggle: () -> Unit) {
    val running = uiState.agentRunning
    val color = if (running) Color(0xFF2E7D32) else Color(0xFFC62828)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(color, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (running) "AKTIV" else "INAKTIV",
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text("⏱ Laufzeit: ${uiState.runtime}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (uiState.duration == "unlimited") "📊 Gesamtdauer: unbegrenzt"
                else "📊 Gesamtdauer: ${uiState.progress.toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { uiState.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (running) "⏹ Agent stoppen" else "▶ Agent starten")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📅 Gesamtdauer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1h" to "1 Std.", "6h" to "6 Std.", "24h" to "24 Std.",
                    "1w" to "1 Woche", "unlimited" to "∞").forEach { (value, label) ->
                    FilterChip(
                        selected = uiState.duration == value,
                        onClick = { viewModel.setDuration(value) },
                        label = { Text(label) }
                    )
                }
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
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.applyCustomDuration() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("✔️ Speichern") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("⏱ Abfrageintervall", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30 Sek.", 60 to "1 Min.", 300 to "5 Min.",
                    900 to "15 Min.", 3600 to "1 Std.").forEach { (value, label) ->
                    FilterChip(
                        selected = uiState.interval == value,
                        onClick = { viewModel.setInterval(value) },
                        label = { Text(label) }
                    )
                }
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
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.applyCustomInterval() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text("✔️ Speichern") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🎯 Priorisierung", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.priority == "high",
                    onClick = { viewModel.setPriority("high") },
                    label = { Text("Hoch") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFC62828).copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.priority == "medium",
                    onClick = { viewModel.setPriority("medium") },
                    label = { Text("Mittel") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF9A825).copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.priority == "low",
                    onClick = { viewModel.setPriority("low") },
                    label = { Text("Niedrig") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.2f)
                    )
                )
            }
            CheckRow(uiState.dynamicPriority, "⚡ Dynamische Anpassung",
                viewModel::setDynamicPriority)
            CheckRow(uiState.learningMode, "🔄 Lernmodus (rekursive Verbesserung)",
                viewModel::setLearningMode)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
