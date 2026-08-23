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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
                        OutlinedTextField(
                            value = state.profileName,
                            onValueChange = viewModel::setProfileName,
                            label = { Text("Benutzer") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.profileOrg,
                            onValueChange = viewModel::setProfileOrg,
                            label = { Text("Organisation") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                        Text("🧪 Demo-Modus",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Im Demo-Modus liefern die Detektions-Kanäle (BLE, WiFi, LoRa, " +
                                "Optik, Urban, Crowd, Satellit) simulierte Daten und es werden " +
                                "5 Beispiel-Assets geladen – gekennzeichnet mit „(Demo)“. " +
                                "Ausgeschaltet arbeitet die App ausschließlich mit echten " +
                                "Messungen; nicht erreichbare Quellen melden ehrlich „nicht " +
                                "gefunden“.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SwitchRow(
                            if (state.demoDataLoaded) "Demo-Modus (Daten geladen)"
                            else "Demo-Modus (simulierte Kanäle)",
                            state.demoMode,
                            viewModel::setDemoMode
                        )
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔗 Backend-Endpunkte (echte Quellen)",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sobald eine URL gesetzt ist, fragt der jeweilige Kanal den echten " +
                                "Endpunkt ab (statt nur im Demo-Modus zu simulieren). " +
                                "Leer = Kanal inaktiv.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        EndpointField(
                            label = "LoRaWAN-Gateways (GET, JSON)",
                            value = state.loraEndpoint,
                            onChange = viewModel::setLoraEndpoint
                        )
                        EndpointField(
                            label = "LoRa API-Key (optional, Bearer)",
                            value = state.loraApiKey,
                            onChange = viewModel::setLoraApiKey,
                            obscure = true
                        )
                        EndpointField(
                            label = "Optik-Inferenz (POST, YOLO-Server)",
                            value = state.opticalEndpoint,
                            onChange = viewModel::setOpticalEndpoint
                        )
                        EndpointField(
                            label = "Urban-Infrastruktur (GET)",
                            value = state.urbanEndpoint,
                            onChange = viewModel::setUrbanEndpoint
                        )
                        EndpointField(
                            label = "Crowd-/Find-My-Proxy (GET)",
                            value = state.crowdEndpoint,
                            onChange = viewModel::setCrowdEndpoint
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndpointField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    obscure: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://…") },
            visualTransformation = if (obscure)
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}
