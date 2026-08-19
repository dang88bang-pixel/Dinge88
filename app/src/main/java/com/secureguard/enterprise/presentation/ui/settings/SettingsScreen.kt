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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    var notifications by remember { mutableStateOf(true) }
    var bluetooth by remember { mutableStateOf(true) }
    var wifi by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf(true) }

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
                            onCheckedChange = { notifications = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "Vibration",
                            checked = true,
                            onCheckedChange = {}
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
                            onCheckedChange = { bluetooth = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "WiFi (Probe Requests)",
                            checked = wifi,
                            onCheckedChange = { wifi = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingRow(
                            label = "Standort",
                            checked = location,
                            onCheckedChange = { location = it }
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
