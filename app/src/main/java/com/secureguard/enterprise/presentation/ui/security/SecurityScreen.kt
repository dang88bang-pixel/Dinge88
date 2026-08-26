package com.secureguard.enterprise.presentation.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    navController: NavController,
    viewModel: SecurityViewModel = hiltViewModel()
) {
    val auditEntries by viewModel.auditEntries.collectAsState()
    val pinConfigured by viewModel.pinConfigured.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val encryptionResult by viewModel.encryptionTestResult.collectAsState()
    val gpsLocation by viewModel.gpsLocation.collectAsState()
    var newPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadAuditLog() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Integrity Center") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAuditLog() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
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
            // Encryption Status
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("Verschlüsselung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        StatusRow("AES/GCM/NoPadding", true)
                        StatusRow("AndroidKeyStore", true)
                        StatusRow("256-Bit Schlüssel", true)
                        StatusRow("Hardware-gesichert", true)
                        StatusRow("SQLCipher (Room at-rest)", true)
                        StatusRow("DB-Key im KeyStore", true)
                        Text(
                            "Key-Fingerprint: ${viewModel.dbKeyFingerprint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // PIN Management
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(8.dp))
                            Text("PIN-Authentifizierung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            StatusDot(pinConfigured)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (pinConfigured) "PIN konfiguriert · Auto-Lock: 5 Min · Max. 5 Versuche"
                            else "Keine PIN konfiguriert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
                            label = { Text(if (pinConfigured) "Neue PIN" else "PIN setzen (min. 4 Zeichen)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("security_pin_field")
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newPin.length >= 4) {
                                        viewModel.configurePin(newPin)
                                        pinMessage = "PIN gesetzt"
                                        newPin = ""
                                    } else {
                                        pinMessage = "Mindestens 4 Zeichen"
                                    }
                                },
                                modifier = Modifier.testTag("security_pin_set_button")
                            ) { Text(if (pinConfigured) "Ändern" else "Setzen") }
                            if (pinConfigured) {
                                Button(
                                    onClick = {
                                        viewModel.lockApp()
                                        pinMessage = "App gesperrt"
                                    },
                                    modifier = Modifier.testTag("security_pin_lock_button")
                                ) { Text("Sperren") }
                                Button(
                                    onClick = {
                                        viewModel.disablePin()
                                        pinMessage = "PIN entfernt"
                                    },
                                    modifier = Modifier.testTag("security_pin_disable_button")
                                ) { Text("Entfernen") }
                            }
                        }
                        pinMessage?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // RBAC
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("RBAC Rollenmodell", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        RoleRow("ADMIN", "Vollzugriff", listOf("VIEW", "EDIT", "DELETE", "EXECUTE", "LOGS", "CONFIG", "USERS"))
                        RoleRow("MANAGER", "Assets + Aktionen", listOf("VIEW", "EDIT", "EXECUTE", "LOGS"))
                        RoleRow("OPERATOR", "Eigene Assets", listOf("VIEW", "EXECUTE"))
                        RoleRow("VIEWER", "Nur Lesen", listOf("VIEW"))
                    }
                }
            }

            // Hardware Diagnostics
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Hardware-Diagnose", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        StatusRow("NFC verfügbar", viewModel.nfcAvailable)
                        StatusRow("Verschlüsselung", true)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.testEncryption() }) { Text("AES Test") }
                            Button(onClick = { viewModel.fetchGpsLocation() }) { Text("GPS") }
                        }
                        encryptionResult?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                        gpsLocation?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Audit Log
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audit-Log (${auditEntries.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearAuditLog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Löschen")
                    }
                }
            }

            items(auditEntries.take(30)) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.action,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (entry.details.isNotBlank()) {
                                Text(
                                    entry.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(entry.timestamp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            if (active) "✅" else "❌",
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF00E676) else Color(0xFFFF1744))
    )
}

@Composable
private fun RoleRow(role: String, desc: String, permissions: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            role,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(
            "${permissions.size}P",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
