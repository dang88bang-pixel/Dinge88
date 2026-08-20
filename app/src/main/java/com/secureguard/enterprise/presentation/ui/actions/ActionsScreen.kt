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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.ActionButton
import com.secureguard.enterprise.presentation.ui.common.ActionType

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
    val menuExpanded by viewModel.menuExpanded.collectAsState()

    var recoverResend by remember { mutableStateOf(true) }
    var logCommands by remember { mutableStateOf(false) }
    var autoNotify by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Aktionen") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Log löschen")
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🎯 Asset auswählen", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = menuExpanded,
                            onExpandedChange = { viewModel.setMenuExpanded(it) }
                        ) {
                            OutlinedTextField(
                                value = selectedAsset?.shortName ?: "Asset auswählen",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { viewModel.setMenuExpanded(false) }
                            ) {
                                assets.forEach { asset ->
                                    DropdownMenuItem(
                                        text = { Text(asset.shortName) },
                                        onClick = { viewModel.selectAsset(asset) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val asset = selectedAsset
            if (asset != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (asset.status == AssetStatus.ONLINE)
                                Color(0xFF2E7D32).copy(alpha = 0.05f)
                            else Color(0xFFC62828).copy(alpha = 0.05f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "🚀 Aktionen für ${asset.shortName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "STATUS: ${asset.status}  |  📶 ${asset.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall
                            )

                            val online = asset.status == AssetStatus.ONLINE && !isExecuting

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.Warning, "🔔 Alarm",
                                    { viewModel.executeAction(ActionType.ALARM) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.Lightbulb, "💡 Blinken",
                                    { viewModel.executeAction(ActionType.LIGHT) }, online)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.PowerSettingsNew, "🔇 Motor",
                                    { viewModel.executeAction(ActionType.MOTOR_OFF) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.BatteryAlert, "🔋 Batterie",
                                    { viewModel.executeAction(ActionType.BATTERY) }, online)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.Message, "📝 Nachricht",
                                    { viewModel.executeAction(ActionType.MESSAGE) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.LocationOn, "📍 Position",
                                    { viewModel.executeAction(ActionType.POSITION) }, online)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionButton(Modifier.weight(1f), Icons.Default.Refresh, "🔄 Neustart",
                                    { viewModel.executeAction(ActionType.RESTART) }, online)
                                ActionButton(Modifier.weight(1f), Icons.Default.Storage, "📊 Telemetrie",
                                    { viewModel.executeAction(ActionType.TELEMETRY) }, online)
                            }

                            if (isExecuting) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Aktion wird ausgeführt...",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⚙️ Einstellungen", style = MaterialTheme.typography.titleSmall)
                            CheckRow(recoverResend, "Recover/Resend aktivieren") { recoverResend = it }
                            CheckRow(logCommands, "Steuerlog aufzeichnen") { logCommands = it }
                            CheckRow(autoNotify, "Automatische Benachrichtigung") { autoNotify = it }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📋 Command Log", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = { viewModel.clearLog() }) { Text("Leeren") }
                            }
                            if (commandLog.isEmpty()) {
                                Text(
                                    "Keine Einträge",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                commandLog.takeLast(10).forEach { entry ->
                                    Text(entry, style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 2.dp))
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
                            Text("Bitte wähle ein Asset aus",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
