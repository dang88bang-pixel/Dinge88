package com.secureguard.enterprise.presentation.ui.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AgentConfigScreen(
    viewModel: AgentViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val status by viewModel.status.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Agent-Konfiguration") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "● ${if (status.running) "AGENT AKTIV" else "AGENT INAKTIV"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (status.running) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Überwachte Assets: ${status.assetsTracked}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.startAgent() },
                            enabled = !status.running
                        ) {
                            Text("Start")
                        }
                        OutlinedButton(
                            onClick = { viewModel.stopAgent() },
                            enabled = status.running
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSlider(
                        label = "Intervall (Sekunden)",
                        value = settings.interval.toFloat(),
                        onValueChange = { viewModel.updateInterval(it.toInt()) }
                    )
                    SettingSwitch(
                        label = "Dynamische Priorität",
                        checked = settings.dynamicPriority,
                        onCheckedChange = { viewModel.updateDynamicPriority(it) }
                    )
                    SettingSwitch(
                        label = "Lernmodus (rekursive Verbesserung)",
                        checked = settings.learningMode,
                        onCheckedChange = { viewModel.updateLearningMode(it) }
                    )
                    SettingSwitch(
                        label = "Nur Offline-Assets suchen",
                        checked = settings.offlineOnly,
                        onCheckedChange = { viewModel.updateOfflineOnly(it) }
                    )
                    SettingSwitch(
                        label = "Externe Quellen (Apple/Google) – Einwilligung nötig",
                        checked = settings.externalSources,
                        onCheckedChange = { viewModel.updateExternalSources(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 10f..300f,
            steps = 28
        )
        Text("${value.toInt()} s", style = MaterialTheme.typography.bodySmall)
    }
}
