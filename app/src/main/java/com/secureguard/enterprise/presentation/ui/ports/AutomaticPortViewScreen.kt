package com.secureguard.enterprise.presentation.ui.ports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.services.AutomaticPortView
import com.secureguard.enterprise.services.AutomaticPortSnapshot
import com.secureguard.enterprise.services.PortProbe
import com.secureguard.enterprise.services.PortState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Automatic Port View – zeigt automatisch (ohne Berechtigungen oder manuelle
 * Aktualisierung) alle abgeleiteten Stack-/Verbindungs-Ports mit Status und
 * Latenz. Datenquelle ist die native [AutomaticPortView]-Bridge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomaticPortViewScreen(
    navController: NavController,
    viewModel: AutomaticPortViewViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsState()
    val autoRefresh by viewModel.autoRefreshEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔌 Port-Ansicht (automatisch)") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (!snapshot.probing) viewModel.probeNow() },
                        modifier = Modifier.testTag("ports_refresh")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Jetzt prüfen")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .testTag("ports_screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SummaryCard(snapshot, autoRefresh, viewModel::setAutoRefresh) }

            if (snapshot.probes.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("⏳ Ziele werden abgeleitet …", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Die Prüfziele kommen automatisch aus deinen Einstellungen " +
                                    "(Einstellungen → Backend & Broker).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(snapshot.probes, key = { it.target.id }) { probe ->
                    PortRow(probe)
                }
            }

            item {
                Text(
                    "Ziele werden automatisch aus den Verbindungs-Einstellungen abgeleitet " +
                        "(MQTT, WebSocket, Backend, MCP/LoRa/YOLO/Find-My) und um die " +
                        "Standard-Stack-Ports 1883/9001/8000/1880 ergänzt. " +
                        "Läuft rein nativ – ohne zusätzliche Berechtigungen, " +
                        "Aktualisierung automatisch alle " +
                        "${AutomaticPortView.AUTO_REFRESH_INTERVAL_MS / 1000} s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    snapshot: AutomaticPortSnapshot,
    autoRefresh: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val headline = when {
                        snapshot.probing -> "⏳ Prüfe Ports …"
                        snapshot.probes.isEmpty() -> "🔌 Automatische Port-Ansicht"
                        snapshot.allOk -> "✅ Alle ${snapshot.probes.size} Ports offen"
                        else -> "⚠ " +
                            "${snapshot.openCount} offen · ${snapshot.closedCount} zu · " +
                            "${snapshot.failingCount} nicht erreichbar"
                    }
                    Text(
                        headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    val ts = snapshot.checkedAt?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "–"
                    Text(
                        "Stand: $ts · automatische Aktualisierung ${if (autoRefresh) "AN" else "PAUSE"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = autoRefresh,
                        onCheckedChange = onAutoRefreshChange,
                        modifier = Modifier.testTag("ports_auto_toggle")
                    )
                    Text(
                        if (autoRefresh) "Auto" else "Manuell",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (snapshot.probes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusChip("✅ ${snapshot.openCount} offen")
                    StatusChip("❌ ${snapshot.closedCount} zu")
                    StatusChip("⚠️ ${snapshot.failingCount} Fehler")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PortRow(probe: PortProbe) {
    val stateEmoji = when (probe.state) {
        PortState.OPEN -> "✅"
        PortState.CLOSED -> "❌"
        PortState.TIMEOUT -> "⚠️"
        PortState.UNREACHABLE -> "🚫"
        null -> "⏳"
    }
    val stateColor = when (probe.state) {
        PortState.OPEN -> Color(0xFF2E7D32)
        PortState.CLOSED, PortState.TIMEOUT, PortState.UNREACHABLE -> Color(0xFFC62828)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stateEmoji, fontSize = 14.sp)
                    Spacer(Modifier.padding(end = 4.dp))
                    Text(
                        probe.target.label,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${probe.target.host}:${probe.target.port}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    probe.target.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (probe.detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        probe.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (probe.state) {
                        PortState.OPEN -> probe.latencyMs?.let { "$it ms" } ?: "offen"
                        else -> "–"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
