package com.secureguard.enterprise.presentation.ui.tempmail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

/**
 * Dashboard für temporäre E-Mail-Inboxes (OTP-Abruf für den Agenten).
 * Nur aktiv, wenn ein MCP-Server konfiguriert ist (MCP_SERVER_URL).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempMailScreen(
    navController: NavController,
    viewModel: TempMailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentInbox by viewModel.currentInbox.collectAsState()
    val lastOTP by viewModel.lastOTP.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📧 Temporäre E-Mail") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearInbox() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Leeren")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!viewModel.isConfigured) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚠️ Kein MCP-Server konfiguriert.\n" +
                            "Setze MCP_SERVER_URL in local.properties, um temporäre " +
                            "E-Mail-Inboxes zu nutzen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Aktuelle Inbox
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (currentInbox != null) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📨 Aktuelle Inbox", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (currentInbox != null) {
                        Text("📧 ${currentInbox?.email}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "🔑 Token: ${currentInbox?.token?.take(20)}…",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "📋 ID: ${currentInbox?.inboxId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Keine Inbox erstellt", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Aktionen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.createInbox() },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("📬 Neue Inbox")
                    }
                }
                Button(
                    onClick = { viewModel.waitForOTP() },
                    modifier = Modifier.weight(1f),
                    enabled = currentInbox != null && !isProcessing
                ) {
                    Text("⏳ OTP abrufen")
                }
            }

            // OTP-Ergebnis
            if (lastOTP != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lastOTP?.success == true) {
                            Color(0x1A4CAF50)
                        } else {
                            Color(0x1AF44336)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            if (lastOTP?.success == true) "✅ OTP empfangen!" else "❌ Kein OTP",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (lastOTP?.success == true) {
                            Text(
                                "🔑 OTP: ${lastOTP?.otp}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "📧 Von: ${lastOTP?.from}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "📋 Betreff: ${lastOTP?.subject}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                lastOTP?.error ?: "Fehler",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Status-Log
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📋 Log", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn {
                        items(uiState.logEntries.takeLast(10)) { entry ->
                            Text(
                                entry,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
