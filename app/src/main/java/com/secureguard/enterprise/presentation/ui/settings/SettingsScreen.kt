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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import com.secureguard.enterprise.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

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
                        androidx.compose.material3.OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::setUserName,
                            label = { Text("Benutzer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.organization,
                            onValueChange = viewModel::setOrganization,
                            label = { Text("Organisation") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔌 Backend & Broker (Runtime)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ohne Rebuild änderbar. Speichern reconnectet MQTT/WebSocket.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.mqttBrokerUrl,
                            onValueChange = viewModel::setMqttBrokerUrl,
                            label = { Text("MQTT Broker (tcp://host:1883)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedTextField(
                                value = state.mqttUsername,
                                onValueChange = viewModel::setMqttUsername,
                                label = { Text("MQTT User") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = state.mqttPassword,
                                onValueChange = viewModel::setMqttPassword,
                                label = { Text("MQTT Pass") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        androidx.compose.material3.OutlinedTextField(
                            value = state.websocketUrl,
                            onValueChange = viewModel::setWebsocketUrl,
                            label = { Text("WebSocket (ws://host:8000/ws)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.backendBaseUrl,
                            onValueChange = viewModel::setBackendBaseUrl,
                            label = { Text("Backend HTTP (http://host:8000)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.mcpServerUrl,
                            onValueChange = viewModel::setMcpServerUrl,
                            label = { Text("MCP / Temp-Mail Server") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.loraGatewayUrl,
                            onValueChange = viewModel::setLoraGatewayUrl,
                            label = { Text("LoRa Gateway URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.yoloServerUrl,
                            onValueChange = viewModel::setYoloServerUrl,
                            label = { Text("YOLO Server URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.openDataApiUrl,
                            onValueChange = viewModel::setOpenDataApiUrl,
                            label = { Text("CKAN / Open Data URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = state.findMyProxyUrl,
                            onValueChange = viewModel::setFindMyProxyUrl,
                            label = { Text("Find-My Proxy URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.saveEndpoints() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Endpunkte speichern") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.syncBackend() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Backend-Sync") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("integrations_card"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🧩 Anbindungen & Abhängigkeiten",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Alle Verbindungen der App (lokal) und des Backends (Server): " +
                                "Endpunkte, Schlüssel, Slack, MQTT, externe APIs. " +
                                "„Status prüfen“ holt die Live-Inventur vom Backend " +
                                "(GET /api/system/dependencies).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider()
                        Text("💬 Slack (MCP)", style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = state.slackEnabled,
                                onCheckedChange = viewModel::setSlackEnabled,
                                modifier = Modifier.testTag("settings_slack_enabled")
                            )
                            Text(
                                "Alarme der App an Slack melden " +
                                    "(läuft über das Backend, Tokens liegen dort)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        androidx.compose.material3.OutlinedTextField(
                            value = state.slackChannel,
                            onValueChange = viewModel::setSlackChannel,
                            label = { Text("Slack-Channel (leer = Backend-Default)") },
                            placeholder = { Text("#secureguard-alerts") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_slack_channel")
                        )
                        Text(
                            "MCP-Server, Bot-Token und Freigaben werden serverseitig " +
                                "konfiguriert (.env → SLACK_MCP_*, docs/SLACK_MCP.md) und " +
                                "unten als Server-Abhängigkeit angezeigt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.refreshIntegrations() },
                                modifier = Modifier.testTag("integrations_refresh_button")
                            ) { Text("Status prüfen") }
                            Text(
                                state.integrationsCheckedAt?.let { "zuletzt $it" }
                                    ?: "noch nicht geprüft",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }

                        HorizontalDivider()
                        Text(
                            "📡 ${state.integrations.size} Einträge",
                            style = MaterialTheme.typography.titleSmall
                        )
                        state.integrations.forEach { info ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("integration_row_${info.id}")
                            ) {
                                Text(
                                    "${info.icon} ${info.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${info.stateLabel} · ${info.target}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    listOf(info.source, info.detail)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider()
                        }
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
                                "deaktiviert und bedürfen ausdrücklicher Zustimmung. " +
                                "Passwörter und PINs legt der Anwender selbst fest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Löschkonzept: Detektionen/Alerts/Audit standardmäßig 90 Tage. " +
                                "Datenauskunft (Art. 15) und vollständige Löschung (Art. 17) unten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.exportDataSubjectAccess() },
                                modifier = Modifier.weight(1f).testTag("privacy_export_button")
                            ) { Text("Datenauskunft") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.applyDataRetention() },
                                modifier = Modifier.weight(1f).testTag("privacy_retention_button")
                            ) { Text("Retention 90d") }
                        }
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.eraseAllLocalData(alsoClearAuth = false) },
                            modifier = Modifier.fillMaxWidth().testTag("privacy_erase_button")
                        ) { Text("Alle lokalen Daten löschen (Art. 17)") }
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
                            "Slack (MCP: Alerts & Meldungen)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("settings_slack_button")
                                .clickable { navController.navigate(Routes.SLACK) }
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
                        HorizontalDivider()
                        Text(
                            "Security & Integrity Center",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.SECURITY) }
                        )
                        HorizontalDivider()
                        Text(
                            "ESP32 Gateway Konfiguration",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.ESP32_CONFIG) }
                        )
                        HorizontalDivider()
                        Text(
                            "System-Health / Monitoring",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.HEALTH) }
                        )
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("🤖 Vordergrund-Dienst", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.startForegroundService() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Starten") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.stopForegroundService() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Stoppen") }
                        }
                        Text(
                            "Hält den Agent aktiv, auch wenn die App im Hintergrund ist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💾 Daten & Export",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.Button(
                                onClick = { viewModel.createBackup() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Backup") }
                            androidx.compose.material3.Button(
                                onClick = { viewModel.exportCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text("CSV") }
                            androidx.compose.material3.Button(
                                onClick = { viewModel.exportPdf() },
                                modifier = Modifier.weight(1f)
                            ) { Text("PDF") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportDetectionsCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Detekt. CSV") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.exportEncryptedCsv() },
                                modifier = Modifier.weight(1f)
                            ) { Text("CSV 🔒") }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.restoreBackup() },
                                modifier = Modifier.weight(1f)
                            ) { Text("Restore") }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${viewModel.listBackups()} Backups verfügbar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Offline-Karten: ${viewModel.getOfflineMapUrl()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Status message
            state.statusMessage?.let { msg ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (msg.startsWith("✅")) Color(0x1A4CAF50) else Color(0x1AF44336)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                            androidx.compose.material3.TextButton(onClick = { viewModel.clearStatus() }) {
                                Text("✕")
                            }
                        }
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
