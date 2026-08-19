package com.secureguard.enterprise.presentation.ui.actions

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.ActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    navController: NavController,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val selectedAsset by viewModel.selectedAsset.collectAsState()
    val commandLog by viewModel.commandLog.collectAsState()
    val isExecuting by viewModel.isExecuting.collectAsState()

    var dropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Aktionen") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Log löschen")
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
            // Asset-Auswahl
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎯 Asset auswählen", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedAsset?.shortName ?: "Asset auswählen",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                assets.forEach { asset ->
                                    DropdownMenuItem(
                                        text = { Text(asset.shortName) },
                                        onClick = {
                                            viewModel.selectAsset(asset)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val sel = selectedAsset
            if (sel != null) {
                // Aktionen
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sel.status == AssetStatus.ONLINE) {
                                Color.Green.copy(alpha = 0.05f)
                            } else {
                                Color.Red.copy(alpha = 0.05f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🚀 Aktionen für ${sel.shortName}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "STATUS: ${sel.status.name}  |  " +
                                    "📶 ${sel.rssi} dBm  |  ⏱ ${sel.lastSeen}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Warning,
                                    label = "🔔 Alarm",
                                    onClick = { viewModel.executeAction(ActionType.ALARM) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Lightbulb,
                                    label = "💡 Blinken",
                                    onClick = { viewModel.executeAction(ActionType.LIGHT) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.PowerSettingsNew,
                                    label = "🔇 Motor aus",
                                    onClick = { viewModel.executeAction(ActionType.MOTOR_OFF) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.BatteryAlert,
                                    label = "🔋 Batterie",
                                    onClick = { viewModel.executeAction(ActionType.BATTERY) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Message,
                                    label = "📝 Nachricht",
                                    onClick = { viewModel.executeAction(ActionType.MESSAGE) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.LocationOn,
                                    label = "📍 Position",
                                    onClick = { viewModel.executeAction(ActionType.POSITION) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Refresh,
                                    label = "🔄 Neustarten",
                                    onClick = { viewModel.executeAction(ActionType.RESTART) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                                ActionButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Filled.Storage,
                                    label = "📊 Telemetrie",
                                    onClick = { viewModel.executeAction(ActionType.TELEMETRY) },
                                    enabled = sel.status == AssetStatus.ONLINE && !isExecuting
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("⚙️ Einstellungen", style = MaterialTheme.typography.titleSmall)
                            SettingCheckboxRow("Recover/Resend aktivieren", true)
                            SettingCheckboxRow("Steuerlog aufzeichnen", false)
                            SettingCheckboxRow("Automatische Benachrichtigung", false)

                            if (isExecuting) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Aktion wird ausgeführt...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Command Log
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("📋 Command Log", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { viewModel.clearLog() }) {
                                    Text("Log löschen")
                                }
                            }
                            if (commandLog.isEmpty()) {
                                Text(
                                    "Keine Einträge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                commandLog.takeLast(10).forEach { entry ->
                                    Text(
                                        text = entry,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Bitte wähle ein Asset aus",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCheckboxRow(label: String, initiallyChecked: Boolean) {
    var checked by remember { mutableStateOf(initiallyChecked) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { checked = it })
        Text(label, modifier = Modifier.padding(top = 8.dp))
    }
}
