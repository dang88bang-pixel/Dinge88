package com.secureguard.enterprise.presentation.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.ActivityLogItem
import java.util.Locale

/**
 * Aktivitätsverlauf (CT45P): vollständige Transparenz über alle Anfragen
 * und Aktionen – Audit-Log (Echtzeit), 24h-Statistik, on-device CT45P-Log
 * und Exporte (CSV / Log-Datei, teilbar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityHistoryScreen(
    navController: NavController,
    viewModel: ActivityHistoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.filteredEntries.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val lastExport by viewModel.lastExport.collectAsState()

    val sourceText = stats.bySource.entries
        .sortedByDescending { it.value }
        .joinToString(", ") { "${it.key} (${it.value})" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📋 Aktivitätsverlauf") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navController.navigateUp() }) {
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == HistoryFilter.ALL,
                        onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                        label = { Text("Alle") }
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.ACTION,
                        onClick = { viewModel.setFilter(HistoryFilter.ACTION) },
                        label = { Text("Aktionen") }
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.AGENT,
                        onClick = { viewModel.setFilter(HistoryFilter.AGENT) },
                        label = { Text("Agent") }
                    )
                    FilterChip(
                        selected = filter == HistoryFilter.REGISTER,
                        onClick = { viewModel.setFilter(HistoryFilter.REGISTER) },
                        label = { Text("Registrierung") }
                    )
                }
            }

            // 24h-Statistik
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("📊 STATISTIK (Letzte 24h)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "✅ Erfolgreich: ${stats.successCount}   |   ❌ Fehler: ${stats.errorCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "📡 Quellen: ${sourceText.ifEmpty { "–" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "⏱ Durchschnitt: " +
                                String.format(Locale.GERMANY, "%.1f s", stats.averageDurationMs / 1000.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // CT45P-Log (on-device)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📁 CT45P-Log (auf dem Gerät)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            viewModel.ct45pLogFilePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Die Datei ist in jedem Dateimanager unter Android/data/… einsehbar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.exportCt45pLog() }) {
                                Text("📦 Log exportieren")
                            }
                            if (lastExport != null) {
                                TextButton(onClick = {
                                    viewModel.shareLastExport()?.let { intent ->
                                        context.startActivity(
                                            Intent.createChooser(intent, "Export teilen")
                                        )
                                    }
                                }) {
                                    Text("📤 Teilen")
                                }
                            }
                        }
                    }
                }
            }

            // Audit-Log-Export
            item {
                Button(
                    onClick = { viewModel.exportAuditCsv() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📥 Audit-Log als CSV exportieren (${entries.size} Einträge)")
                }
            }

            // Einträge
            items(entries) { entry ->
                ActivityLogItem(entry)
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "Noch keine Einträge – der Verlauf füllt sich, sobald der Agent " +
                            "läuft oder Aktionen ausgeführt werden.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }
}
