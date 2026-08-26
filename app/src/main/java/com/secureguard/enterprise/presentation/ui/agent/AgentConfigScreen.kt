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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.ExperimentalMaterial3Api
import com.secureguard.enterprise.R

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
                title = { Text(stringResource(R.string.title_agent_config)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.saveSettings() }) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.cd_save))
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
            item { StatusCard(uiState) }
            item { DurationCard(uiState, viewModel) }
            item { IntervalCard(uiState, viewModel) }
            item { PriorityCard(uiState, viewModel) }
            item {
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) { Text(stringResource(R.string.btn_save_config)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusCard(uiState: AgentUiState) {
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
                        stringResource(if (running) R.string.agent_active else R.string.agent_inactive),
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(stringResource(R.string.agent_runtime, uiState.runtime), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.agent_progress, uiState.progress.toInt()),
                style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { uiState.progress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.section_duration), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "1h" to stringResource(R.string.duration_1h),
                    "6h" to stringResource(R.string.duration_6h),
                    "24h" to stringResource(R.string.duration_24h),
                    "1w" to stringResource(R.string.duration_1w),
                    "unlimited" to stringResource(R.string.duration_unlimited)
                ).forEach { (value, label) ->
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
                    label = { Text(stringResource(R.string.label_days)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.applyCustomDuration() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text(stringResource(R.string.btn_save_check)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntervalCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.section_interval), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    30 to stringResource(R.string.interval_30s),
                    60 to stringResource(R.string.interval_1m),
                    300 to stringResource(R.string.interval_5m),
                    900 to stringResource(R.string.interval_15m),
                    3600 to stringResource(R.string.interval_1h)
                ).forEach { (value, label) ->
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
                    label = { Text(stringResource(R.string.label_seconds)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = { viewModel.applyCustomInterval() },
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) { Text(stringResource(R.string.btn_save_check)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityCard(uiState: AgentUiState, viewModel: AgentViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.section_priority), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.priority == "high",
                    onClick = { viewModel.setPriority("high") },
                    label = { Text(stringResource(R.string.priority_high)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFC62828).copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.priority == "medium",
                    onClick = { viewModel.setPriority("medium") },
                    label = { Text(stringResource(R.string.priority_medium)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF9A825).copy(alpha = 0.2f)
                    )
                )
                FilterChip(
                    selected = uiState.priority == "low",
                    onClick = { viewModel.setPriority("low") },
                    label = { Text(stringResource(R.string.priority_low)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.2f)
                    )
                )
            }
            CheckRow(uiState.dynamicPriority, stringResource(R.string.check_dynamic_priority),
                viewModel::setDynamicPriority)
            CheckRow(uiState.learningMode, stringResource(R.string.check_learning_mode),
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
