package com.secureguard.enterprise.presentation.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgEmptyState
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.designsystem.SgSecondaryButton
import com.secureguard.enterprise.presentation.designsystem.SgStatusBadge
import com.secureguard.enterprise.presentation.designsystem.SgStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    navController: NavController,
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val alerts by viewModel.visibleAlerts.collectAsState()
    val filter by viewModel.uiState.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🚨 Alarme") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    SgIconButton(
                        icon = Icons.Default.DoneAll,
                        contentDescription = "Alle quittieren",
                        onClick = { viewModel.acknowledgeAll() }
                    )
                    SgIconButton(
                        icon = Icons.Default.DeleteSweep,
                        contentDescription = "Abgeschlossene entfernen",
                        onClick = { viewModel.deleteResolved() }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter-Chips (Kategorien, mit Text).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertFilter.entries.forEach { f ->
                    val selected = filter.filter == f
                    SgSecondaryButton(
                        text = f.label,
                        onClick = { viewModel.setFilter(f) },
                        modifier = Modifier.weight(1f),
                        enabled = !selected
                    )
                }
            }

            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp)
                )
                return@Column
            }

            if (alerts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SgEmptyState(
                        icon = Icons.Default.NotificationsOff,
                        title = "Keine Alarme",
                        message = "Für den gewählten Filter (${filter.filter.label}) liegen keine Alarme vor.",
                        actionLabel = "Aktualisieren",
                        onAction = { viewModel.retry() }
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertRow(
                        alert = alert,
                        onAcknowledge = { viewModel.acknowledge(alert.id) },
                        onResolve = { viewModel.resolve(alert.id) }
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SgSecondaryButton(
                        text = "Aktualisieren",
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.retry() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Ende-zu-Ende-Zeile mit Inline-Quittierung (§9). */
@Composable
private fun AlertRow(
    alert: Alert,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit
) {
    val severity = when (alert.severity) {
        AlertSeverity.CRITICAL -> SgStatus.ALARM
        AlertSeverity.WARNING -> SgStatus.WARNING
        AlertSeverity.INFO -> SgStatus.INFO
    }
    val stateText = when {
        alert.resolved -> "abgeschlossen"
        alert.acknowledged -> "quittiert"
        else -> "aktiv"
    }

    SgCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (alert.resolved) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Priorität ${alert.severity}",
                        tint = severity.color,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${alert.type}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                SgStatusBadge(status = severity)
            }
            Text(
                alert.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Asset: ${alert.assetId} · ${formatTime(alert.timestamp)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Status: $stateText",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = severity.color
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!alert.acknowledged && !alert.resolved) {
                    TextButton(onClick = onAcknowledge) { Text("Quittieren", color = severity.color) }
                }
                if (!alert.resolved) {
                    TextButton(onClick = onResolve) { Text("Abschließen", color = severity.color) }
                }
            }
        }
    }
}

private fun formatTime(date: Date?): String {
    if (date == null) return "–"
    return SimpleDateFormat("dd.MM. HH:mm:ss", Locale.getDefault()).format(date)
}
