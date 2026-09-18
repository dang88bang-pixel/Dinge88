package com.secureguard.enterprise.presentation.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.navigation.Routes

/**
 * Help / Support (§21): Bedienung, Berechtigungen, Troubleshooting, Support.
 * Navigation verlinkt auf die realen Screens (Settings, Security, Health).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hilfe & Support") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HelpSection(icon = Icons.Default.Info, title = "Bedienung") {
                Text("• Dashboard zeigt Flotte, Alarme und Agent-Status.")
                Text("• Assets anlegen über die Asset-Liste (Plus-Button) oder QR-Scan.")
                Text("• Aktionen laufen über das Actions Center mit Risiko-Bestätigung.")
                Text("• Das 3D Operations Center stellt die Flotte räumlich dar.")
            }
            HelpSection(icon = Icons.Default.Lock, title = "Berechtigungen") {
                Text("Die App fragt Standort, Kamera, Benachrichtigungen und (ab Android 12) Bluetooth-Berechtigungen ab.")
                Text("Hintergrund-Standort wird erst NACH der Vordergrund-Freigabe angefragt.")
                Text("Hardware (BLE/USB/NFC/GPS) ist optional und wird bei Fehlen gekennzeichnet.")
            }
            HelpSection(icon = Icons.Default.Build, title = "Troubleshooting") {
                Text("• Kein Signal? Prüfe die Berechtigungen und den Systemzustand.")
                Text("• USB/Seriell: Adapter an- und abstecken; die Port-Ansicht aktualisiert sich automatisch.")
                Text("• 3D-Ansicht zeigt SOURCE: SIMULATION, wenn keine echten Daten vorliegen.")
                Text("• Details unter ‚System Health': dort siehst du den Zustand jeder Komponente.")
            }
            HelpSection(icon = Icons.Default.SettingsSuggest, title = "Support") {
                Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text("Repository: dang88bang-pixel/Dinge88")
                Text("Secrets/Signierung laufen ausschließlich über GitHub Secrets.")
            }

            SgCard(onClick = { navController.navigate(Routes.SETTINGS) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Einstellungen öffnen", fontWeight = FontWeight.SemiBold)
                    Text("Berechtigungen, Dark Mode, Benachrichtigungen und Endpunkte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SgCard(onClick = { navController.navigate(Routes.HEALTH) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("System Health öffnen", fontWeight = FontWeight.SemiBold)
                    Text("Zustand von App, DB, Agent, Netzwerk und Hardware prüfen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HelpSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    SgCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}
