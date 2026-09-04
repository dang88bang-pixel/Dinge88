package com.secureguard.enterprise.presentation.ui.slack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

/**
 * Slack-Integration (MCP-Server: provectus/slack-mcp-server).
 *
 * Zeigt Status/Tools/Channels des Servers und versendet Meldungen – alles über
 * das SecureGuard-Backend (`/api/slack/*`), die Slack-Tokens verbleiben dort.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlackScreen(
    navController: NavController,
    viewModel: SlackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val health by viewModel.health.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💬 Slack (MCP)") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshStatus() },
                        modifier = Modifier.testTag("slack_refresh_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Status prüfen")
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!viewModel.isConfigured) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "⚠️ Kein Backend konfiguriert.\n" +
                            "Setze BACKEND_BASE_URL (local.properties) oder die " +
                            "Backend-URL in den Einstellungen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // ---- Status ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("slack_status_card"),
                colors = CardDefaults.cardColors(
                    containerColor = when (health?.reachable) {
                        true -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        false -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛰️ Slack-MCP-Server", style = MaterialTheme.typography.titleMedium)
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .height(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val status = health
                    if (status == null) {
                        Text("Kein Status abgerufen", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            when {
                                !status.configured -> "Nicht konfiguriert (SLACK_MCP_URL im Backend)"
                                status.reachable == true ->
                                    "Verbunden · ${status.serverName} ${status.serverVersion}"
                                else -> "Nicht erreichbar: ${status.error ?: "unbekannt"}"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Endpunkt: ${status.url.ifBlank { "–" }} · Transport: " +
                                status.transport.ifBlank { "–" } +
                                " · Tools: ${status.tools}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Benachrichtigungen: " +
                                (if (status.notifyEnabled) "aktiv" else "inaktiv") +
                                " → ${status.notifyChannel.ifBlank { "–" }}" +
                                " (ab ${status.minSeverity.ifBlank { "WARNING" }})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.loadTools() },
                            modifier = Modifier.testTag("slack_tools_button")
                        ) { Text("Tools") }
                        OutlinedButton(
                            onClick = { viewModel.loadChannels() },
                            modifier = Modifier.testTag("slack_channels_button")
                        ) { Text("Channels") }
                        OutlinedButton(
                            onClick = { viewModel.sendTestMessage() },
                            modifier = Modifier.testTag("slack_test_button")
                        ) { Text("Testmeldung") }
                    }
                }
            }

            // ---- Nachricht ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✍️ Nachricht senden", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.targetChannel,
                        onValueChange = { viewModel.updateTargetChannel(it) },
                        label = { Text("Channel (leer = Default)") },
                        placeholder = { Text("#secureguard-alerts") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slack_channel_field")
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.draft,
                        onValueChange = { viewModel.updateDraft(it) },
                        label = { Text("Nachricht (Markdown erlaubt)") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slack_message_field")
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.sendDraft() },
                        enabled = uiState.draft.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slack_send_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Senden")
                    }
                }
            }

            // ---- Channels / Tools / Log ----
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("slack_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.channels.isNotEmpty()) {
                    item {
                        Text(
                            "📋 Channels (${uiState.channels.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    items(uiState.channels, key = { it.id.ifBlank { it.name } }) { channel ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectChannel(channel) }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    channel.name.ifBlank { channel.id },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    listOfNotNull(
                                        channel.id.takeIf { it.isNotBlank() },
                                        channel.memberCount.takeIf { it.isNotBlank() }
                                            ?.let { "$it Mitglieder" },
                                        channel.topic.takeIf { it.isNotBlank() }
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "History lesen",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { viewModel.loadHistory(channel) }
                                )
                            }
                        }
                    }
                }

                if (uiState.tools.isNotEmpty()) {
                    item {
                        Text(
                            "🧰 MCP-Tools (${uiState.tools.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    items(uiState.tools, key = { it.name }) { tool ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                                if (tool.description.isNotBlank()) {
                                    Text(
                                        tool.description.take(160),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.logEntries.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(4.dp))
                        Text("📜 Verlauf", style = MaterialTheme.typography.titleSmall)
                    }
                    items(uiState.logEntries.reversed()) { entry ->
                        Text(
                            entry,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 20.dp)
                        )
                    }
                }
            }
        }
    }
}
