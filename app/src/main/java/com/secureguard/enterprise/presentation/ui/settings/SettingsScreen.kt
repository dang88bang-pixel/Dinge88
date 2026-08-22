package com.secureguard.enterprise.presentation.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val pinMessage by viewModel.pinMessage.collectAsState()
    var pin by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshPermissions() }

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
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "👤 Profil & Sicherheit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = viewModel::setUserName,
                            label = { Text("Benutzer") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.organization,
                            onValueChange = viewModel::setOrganization,
                            label = { Text("Organisation") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        HorizontalDivider()
                        Text("PIN-Sperre", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("Neue PIN (mind. 4 Zeichen)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.configurePin(pin); pin = "" }) {
                                Text("PIN setzen")
                            }
                            if (viewModel.pinConfigured) {
                                TextButton(onClick = { viewModel.disablePin() }) {
                                    Text("PIN entfernen")
                                }
                            }
                        }
                        pinMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔔 Benachrichtigungen", style = MaterialTheme.typography.titleMedium)
                        SwitchRow("Push-Benachrichtigungen", state.notificationsEnabled, viewModel::setNotifications)
                        HorizontalDivider()
                        SwitchRow("Nur im Offline-Modus arbeiten", state.offlineOnly, viewModel::setOfflineOnly)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🌐 Verbindungen", style = MaterialTheme.typography.titleMedium)
                        SwitchRow(
                            "Externe Crowdsource-Suche (Apple/Google)",
                            state.externalCrowdAllowed,
                            viewModel::setExternalCrowd
                        )
                        HorizontalDivider()
                        SwitchRow("Selbstlernender Agent", state.learningMode, viewModel::setLearning)
                        HorizontalDivider()
                        SwitchRow("Dunkelmodus", state.darkMode, viewModel::setDarkMode)
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔑 Berechtigungen", style = MaterialTheme.typography.titleMedium)
                        permissions.forEach { row ->
                            Text(
                                "${if (row.granted) "✅" else "❌"} ${row.name}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(viewModel.requiredPermissionArray()) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Berechtigungen anfordern")
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🛰️ Backend-Endpunkte (optional)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Leer = Demo-Kanal. Mit URL nutzt der Agent den konfigurierten Dienst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = state.loraEndpoint,
                            onValueChange = viewModel::setLoraEndpoint,
                            label = { Text("LoRa / LoRaWAN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.opticalEndpoint,
                            onValueChange = viewModel::setOpticalEndpoint,
                            label = { Text("Optik / YOLO") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.urbanEndpoint,
                            onValueChange = viewModel::setUrbanEndpoint,
                            label = { Text("Urban / Open Data") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.crowdEndpoint,
                            onValueChange = viewModel::setCrowdEndpoint,
                            label = { Text("Crowd / Find-My-Proxy") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🛡️ Datenschutz (DSGVO)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = state.consentGiven,
                                onCheckedChange = viewModel::setConsent
                            )
                            Text(
                                "Ich willige in die Verarbeitung meiner Daten gemäß Betriebsvereinbarung ein."
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Alle Ortungsdaten werden lokal gespeichert. Externe Kanäle (Crowd/Satellit) " +
                                "sind standardmäßig deaktiviert und bedürfen ausdrücklicher Zustimmung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📡 Erweiterte Werkzeuge", style = MaterialTheme.typography.titleMedium)
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
                        Text(
                            "Agent-Konfiguration",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { navController.navigate(Routes.AGENT_CONFIG) }
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
