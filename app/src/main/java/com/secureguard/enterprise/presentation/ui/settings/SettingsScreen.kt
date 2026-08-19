package com.secureguard.enterprise.presentation.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val notifications by viewModel.repository.notifications.collectAsState()
    val vibration by viewModel.repository.vibration.collectAsState()
    val bluetooth by viewModel.repository.bluetooth.collectAsState()
    val wifi by viewModel.repository.wifi.collectAsState()
    val location by viewModel.repository.location.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Einstellungen") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Benachrichtigungen") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingRow(
                            label = "Push-Benachrichtigungen",
                            checked = notifications,
                            onCheckedChange = viewModel.repository::setNotifications
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "Vibration",
                            checked = vibration,
                            onCheckedChange = viewModel.repository::setVibration
                        )
                    }
                }
            }

            item { SectionTitle("Verbindungen") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingRow(
                            label = "Bluetooth / BLE",
                            checked = bluetooth,
                            onCheckedChange = viewModel.repository::setBluetooth
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "WiFi (ScanResults)",
                            checked = wifi,
                            onCheckedChange = viewModel.repository::setWifi
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "Standort (GPS)",
                            checked = location,
                            onCheckedChange = viewModel.repository::setLocation
                        )
                    }
                }
            }

            item { SectionTitle("Agent") }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("agent_config") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Agent-Konfiguration",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null
                        )
                    }
                }
            }

            item { SectionTitle("Profil") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Name: SecureGuard Admin", style = MaterialTheme.typography.bodyMedium)
                        Text("Firma: Muster GmbH", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "🔒 DSGVO-konform – Betriebsvereinbarung nach § 87 BetrVG.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingRow(
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
