package com.secureguard.enterprise.presentation.ui.esp32

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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Esp32ConfigScreen(
    navController: NavController,
    viewModel: Esp32ConfigViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val lastCommand by viewModel.lastCommand.collectAsState()
    val usbDevices by viewModel.usbDevices.collectAsState()
    var wifiSsid by remember { mutableStateOf("SECUREGUARD") }
    var wifiPass by remember { mutableStateOf("") }
    var mqttHost by remember { mutableStateOf("192.168.1.100") }
    var mqttPort by remember { mutableStateOf("1883") }
    var selectedMac by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32 Gateway Konfiguration") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Target Device
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.padding(end = 8.dp))
                            Text("Ziel-Gerät", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (assets.isEmpty()) {
                            Text("Keine Assets vorhanden", style = MaterialTheme.typography.bodySmall)
                        } else {
                            assets.forEach { asset ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(asset.shortName, fontWeight = FontWeight.SemiBold)
                                        Text(asset.mac, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(
                                        onClick = { selectedMac = asset.mac }
                                    ) { Text(if (selectedMac == asset.mac) "✓" else "Wählen") }
                                }
                            }
                        }
                    }
                }
            }

            // WiFi Config
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.padding(end = 8.dp))
                            Text("WiFi Konfiguration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wifiSsid, onValueChange = { wifiSsid = it },
                            label = { Text("SSID") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = wifiPass, onValueChange = { wifiPass = it },
                            label = { Text("Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // MQTT Config
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("MQTT Broker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = mqttHost, onValueChange = { mqttHost = it },
                            label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = mqttPort, onValueChange = { mqttPort = it },
                            label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Send Config
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("CONFIG-Befehl senden", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sendet die Konfiguration via MQTT an das ESP32-Gateway. " +
                                "Das Gateway speichert die Werte im NVS und startet neu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        val configJson = """{"wifi_ssid":"$wifiSsid","wifi_pass":"$wifiPass","mqtt_host":"$mqttHost","mqtt_port":$mqttPort}"""
                        Text(
                            configJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.sendConfig(selectedMac, configJson) },
                            enabled = selectedMac.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.padding(end = 8.dp))
                            Text("An ${selectedMac.ifBlank { "–" }} senden")
                        }
                        lastCommand?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // USB-Serial Scan
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("USB-Serial Adapter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.scanUsbDevices() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Adapter scannen") }
                        usbDevices?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ESP32 Firmware Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Firmware-Info", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        InfoRow("Plattform", "ESP32 (Arduino)")
                        InfoRow("LoRa", "SX1278 · 868 MHz")
                        InfoRow("BLE Service", "6BA1B218-…-FD13")
                        InfoRow("Befehle", "ALARM, LIGHT, MOTOR_OFF, BATTERY, MESSAGE, POSITION, RESTART, TELEMETRY, CONFIG")
                        InfoRow("NVS-Keys", "wifi_ssid, wifi_pass, mqtt_host, mqtt_port, device_id")
                        InfoRow("ADC", "GPIO34 (Batterie, 100k/100k Teiler)")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}
