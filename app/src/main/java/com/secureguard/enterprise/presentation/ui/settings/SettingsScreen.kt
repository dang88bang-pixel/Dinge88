package com.secureguard.enterprise.presentation.ui.settings

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import com.secureguard.enterprise.presentation.navigation.Routes
import com.secureguard.enterprise.security.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    // Verbindungsstatus beim Öffnen live erfassen (echter Dienstzustand).
    LaunchedEffect(Unit) { viewModel.refreshConnectionStatus() }

    // Laufzeit-Berechtigungen für die Erkennungskanäle (Standort, Bluetooth,
    // WiFi, Benachrichtigungen) anfragen – ohne sie laufen die Kanäle nicht.
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshConnectionStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚙️ Einstellungen") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("👤 Profil & Sicherheit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "App-Sperre: ${if (state.pinConfigured) "PIN aktiv" else "keine PIN eingerichtet"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Version: ${state.appVersion} · Gerät: ${state.deviceModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🌐 Verbindungsstatus",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "MQTT-Broker: ${if (state.mqttConnected) "verbunden" else "getrennt"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "WebSocket: " + when {
                                state.websocketConnected -> "verbunden"
                                state.websocketConfigured -> "konfiguriert, getrennt"
                                else -> "nicht konfiguriert"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "MCP-Server: ${if (state.mcpConfigured) "konfiguriert" else "nicht konfiguriert"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Agent: ${if (state.agentRunning) "läuft" else "gestoppt"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔔 Benachrichtigungen",
                            style = MaterialTheme.typography.titleMedium)
                        SwitchRow("Push-Benachrichtigungen", state.notificationsEnabled,
                            viewModel::setNotifications)
                        HorizontalDivider()
                        SwitchRow("Nur im Offline-Modus arbeiten", state.offlineOnly,
                            viewModel::setOfflineOnly)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔐 Berechtigungen",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        if (state.missingPermissions.isEmpty()) {
                            Text(
                                "Alle Laufzeit-Berechtigungen erteilt (Standort, " +
                                    "Bluetooth, WiFi, Kamera, Benachrichtigungen).",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                "Fehlend: ${state.missingPermissions.size} — ohne diese " +
                                    "Berechtigungen bleiben die Erkennungskanäle inaktiv.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            state.missingPermissions.forEach { perm ->
                                Text(
                                    "• $perm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        com.secureguard.enterprise.presentation.ui.common
                                            .requiredPermissions()
                                    )
                                }
                            ) {
                                Text("Berechtigungen erteilen")
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🌐 Verbindungen", style = MaterialTheme.typography.titleMedium)
                        SwitchRow("Externe Crowdsource-Suche (Apple/Google)",
                            state.externalCrowdAllowed, viewModel::setExternalCrowd)
                        HorizontalDivider()
                        SwitchRow("Selbstlernender Agent", state.learningMode,
                            viewModel::setLearning)
                        HorizontalDivider()
                        SwitchRow("Dunkelmodus", state.darkMode, viewModel::setDarkMode)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🛡️ Datenschutz (DSGVO)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.consentGiven,
                                onCheckedChange = viewModel::setConsent
                            )
                            Text(
                                "Ich willige in die Verarbeitung meiner Daten gemäß " +
                                    "Betriebsvereinbarung ein."
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Alle Ortungsdaten werden ausschließlich lokal auf dem Gerät " +
                                "gespeichert. Externe Kanäle (Crowd/Satellit) sind standardmäßig " +
                                "deaktiviert und bedürfen ausdrücklicher Zustimmung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("👤 Rolle (RBAC)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Aktuelle Rolle: ${state.userRole}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Role.values().forEach { role ->
                                androidx.compose.material3.FilterChip(
                                    selected = state.userRole == role.name,
                                    onClick = { viewModel.setUserRole(role) },
                                    label = { Text(role.name) }
                                )
                            }
                        }
                        Text(
                            "VIEWER/OPERATOR sperren Aktionen und Löschen real " +
                                "(Prüfung über RoleManager bei jeder Ausführung).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💾 Daten (Export & Sicherung)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportAssetsCsv() },
                                enabled = !state.isDataActionRunning,
                                modifier = Modifier.weight(1f)
                            ) { Text("Assets CSV") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportDetectionsCsv() },
                                enabled = !state.isDataActionRunning,
                                modifier = Modifier.weight(1f)
                            ) { Text("Detektionen CSV") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportEncryptedAssetsCsv() },
                                enabled = !state.isDataActionRunning,
                                modifier = Modifier.weight(1f)
                            ) { Text("🔒 CSV verschlüsselt") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.createBackup() },
                                enabled = !state.isDataActionRunning,
                                modifier = Modifier.weight(1f)
                            ) { Text("Backup erstellen") }
                        }
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.restoreLatestBackup() },
                            enabled = !state.isDataActionRunning && state.backupCount > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Neuestes Backup wiederherstellen (${state.backupCount} vorhanden, aktiv nach Neustart)") }
                        if (state.dataActionMessage.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                state.dataActionMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔌 USB-Diagnose (Serial-Adapter)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.refreshUsbDevices() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Angeschlossene Adapter suchen") }
                        if (state.usbDevices.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            state.usbDevices.forEach { device ->
                                Text(
                                    "• $device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Keine USB-Serial-Geräte gefunden (FTDI/CP210x/CH34x werden beim Anschließen erkannt).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔋 Hintergrundbetrieb",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Für einen zuverlässigen 15-Minuten-Takt kann die App von der " +
                                "Batterieoptimierung ausgenommen werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Batterieoptimierung ausnehmen") }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📡 Erweiterte Werkzeuge",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Abfrageknoten (Status, Ratenlimits, Ein/Aus)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.NODE_STATUS) }
                        )
                        HorizontalDivider()
                        Text(
                            "Temporäre E-Mail (OTP für Registrierungen)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.TEMP_MAIL) }
                        )
                    }
                }
            }

            item {
                Text(
                    "SecureGuard Enterprise v1.0.0 · generisches LoRa/LoRaWAN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
