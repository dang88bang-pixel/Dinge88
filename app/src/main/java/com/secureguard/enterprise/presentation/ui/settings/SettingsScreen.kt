package com.secureguard.enterprise.presentation.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

    val loRaUrl by viewModel.repository.loRaUrl.collectAsState()
    val opticalUrl by viewModel.repository.opticalUrl.collectAsState()
    val urbanUrl by viewModel.repository.urbanUrl.collectAsState()
    val crowdUrl by viewModel.repository.crowdUrl.collectAsState()

    val profileName by viewModel.repository.profileName.collectAsState()
    val profileCompany by viewModel.repository.profileCompany.collectAsState()

    val context = LocalContext.current

    // Laufzeit-Berechtigungs-Launcher.
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    val bluetoothPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Aktualisiert die angezeigten Berechtigungs-Status nach der Systemrückgabe.
    var permissionTick by remember { mutableStateOf(0) }
    val locationGranted = remember(permissionTick) { hasLocationPermission(context) }
    val bluetoothGranted = remember(permissionTick) { hasBluetoothPermission(context) }
    val notifGranted = remember(permissionTick) { hasNotificationPermission(context) }

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
            item { SectionTitle("Berechtigungen") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionRow(
                            label = "Standort (GPS / WiFi-Scan)",
                            granted = locationGranted,
                            onClick = {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                                permissionTick++
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        PermissionRow(
                            label = "Bluetooth / BLE",
                            granted = bluetoothGranted,
                            onClick = {
                                bluetoothPermLauncher.launch(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        arrayOf(
                                            Manifest.permission.BLUETOOTH_SCAN,
                                            Manifest.permission.BLUETOOTH_CONNECT
                                        )
                                    } else {
                                        arrayOf(Manifest.permission.BLUETOOTH)
                                    }
                                )
                                permissionTick++
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        PermissionRow(
                            label = "Benachrichtigungen",
                            granted = notifGranted,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionTick++
                            }
                        )
                    }
                }
            }

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

            item { SectionTitle("Backend-Endpunkte (Pilot)") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Leer lassen, wenn die Quelle (noch) nicht verfügbar ist. " +
                                "Die App fragt nur konfigurierte Endpunkte ab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        UrlField("LoRa / LoRaWAN", loRaUrl, viewModel.repository::setLoRaUrl)
                        UrlField("Optik (YOLO)", opticalUrl, viewModel.repository::setOpticalUrl)
                        UrlField("Urbane Infrastruktur", urbanUrl, viewModel.repository::setUrbanUrl)
                        UrlField("Crowd (Find My)", crowdUrl, viewModel.repository::setCrowdUrl)
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
                        Text(
                            "Diese Angaben erscheinen in Berichten und Protokollen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = viewModel.repository::setProfileName,
                            label = { Text("Ihr Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                        OutlinedTextField(
                            value = profileCompany,
                            onValueChange = viewModel.repository::setProfileCompany,
                            label = { Text("Firma") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            item { SectionTitle("Über die App") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("SecureGuard Enterprise", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "🧠 Selbstlernender Ortungs-Agent für Firmen-Assets. " +
                                "Alle Daten werden lokal gespeichert.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "🔒 DSGVO-konform – Pilot-Projekt. " +
                                "Die Betriebsvereinbarung ist als Blaupause hinterlegt, aber noch nicht angebunden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

@Composable
private fun UrlField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onClick: () -> Unit
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
        Text(
            text = if (granted) "✅ Erteilt" else "❌ Nicht erteilt",
            style = MaterialTheme.typography.bodySmall,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(if (granted) "Erneut" else "Anfragen")
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun hasBluetoothPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
            PackageManager.PERMISSION_GRANTED
    }

private fun hasNotificationPermission(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
