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
                        Text(
                            "${viewModel.listBackups()} Backups verfügbar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
