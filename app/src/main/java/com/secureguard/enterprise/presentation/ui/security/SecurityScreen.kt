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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.R
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
                title = { Text(stringResource(R.string.title_security)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAuditLog() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_refresh))
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
                            Text(stringResource(R.string.label_encryption), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        StatusRow(stringResource(R.string.enc_aes_gcm), true)
                        StatusRow(stringResource(R.string.enc_keystore), true)
                        StatusRow(stringResource(R.string.enc_256bit), true)
                        StatusRow(stringResource(R.string.enc_hardware), true)
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
                            Text(stringResource(R.string.label_pin_auth), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            StatusDot(pinConfigured)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(if (pinConfigured) R.string.pin_configured_info else R.string.pin_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { newPin = it },
                            label = { Text(stringResource(if (pinConfigured) R.string.label_new_pin else R.string.label_set_pin)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newPin.length >= 4) {
                                        viewModel.configurePin(newPin)
                                        pinMessage = stringResource(R.string.pin_set)
                                        newPin = ""
                                    } else {
                                        pinMessage = stringResource(R.string.pin_min_length)
                                    }
                                }
                            ) { Text(stringResource(if (pinConfigured) R.string.btn_change else R.string.btn_set)) }
                            if (pinConfigured) {
                                Button(
                                    onClick = {
                                        viewModel.lockApp()
                                        pinMessage = stringResource(R.string.pin_message_locked)
                                    }
                                ) { Text(stringResource(R.string.btn_lock)) }
                                Button(
                                    onClick = {
                                        viewModel.disablePin()
                                        pinMessage = stringResource(R.string.pin_message_removed)
                                    }
                                ) { Text(stringResource(R.string.btn_remove)) }
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
                        Text(stringResource(R.string.label_rbac), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        RoleRow("ADMIN", stringResource(R.string.rbac_admin_desc), listOf("VIEW", "EDIT", "DELETE", "EXECUTE", "LOGS", "CONFIG", "USERS"))
                        RoleRow("MANAGER", stringResource(R.string.rbac_manager_desc), listOf("VIEW", "EDIT", "EXECUTE", "LOGS"))
                        RoleRow("OPERATOR", stringResource(R.string.rbac_operator_desc), listOf("VIEW", "EXECUTE"))
                        RoleRow("VIEWER", stringResource(R.string.rbac_viewer_desc), listOf("VIEW"))
                    }
                }
            }

            // Hardware Diagnostics
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.label_hardware_diag), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        StatusRow(stringResource(R.string.diag_nfc), viewModel.nfcAvailable)
                        StatusRow(stringResource(R.string.label_encryption), true)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.testEncryption() }) { Text(stringResource(R.string.btn_aes_test)) }
                            Button(onClick = { viewModel.fetchGpsLocation() }) { Text(stringResource(R.string.btn_gps)) }
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
                    Text(stringResource(R.string.label_audit_log, auditEntries.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearAuditLog() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.cd_delete))
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
