package com.secureguard.enterprise.presentation.ui.sensorfusion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgEmptyState
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader
import com.secureguard.enterprise.presentation.designsystem.SgStatus
import com.secureguard.enterprise.presentation.designsystem.SgStatusBadge
import kotlinx.coroutines.delay

/**
 * Sensor Fusion (§12): zeigt ausschließlich vorhandene Sensorquellen aus den
 * echten Detektionsdaten (Repository). Quelle + letzte Aktualisierungszeit je
 * Kanal. Keine erfundenen Rohwerte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorFusionScreen(
    navController: NavController,
    viewModel: SensorFusionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lastCheck by viewModel.lastCheck.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(10_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Fusion") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    SgIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Aktualisieren",
                        onClick = { viewModel.refresh() }
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SgCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SgSectionHeader(title = "Quellen")
                        Text(
                            "Stand: $lastCheck · NFC: ${if (state.nfcAvailable) "verfügbar" else "nicht verfügbar"} · USB-Seriell-Adapter: ${state.usbDevices}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.sources.isEmpty()) {
                item {
                    SgEmptyState(
                        icon = Icons.Default.Sensors,
                        title = "Keine Sensordaten",
                        message = "Noch keine Detektionen über einen Sensor geliefert – Daten erscheinen hier automatisch."
                    )
                }
            } else {
                items(state.sources) { channel ->
                    SgCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.label, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Letzte Aktualisierung: ${channel.lastUpdate}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Detektionen: ${channel.lastCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SgStatusBadge(
                                status = if (channel.detected) SgStatus.HEALTHY else SgStatus.WARNING
                            )
                        }
                    }
                }
            }

            state.lastDetection?.let { last ->
                item {
                    SgCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SgSectionHeader(title = "Letzte Detektion")
                            Text("Quelle: ${last.sourceType} · ${last.assetMac}",
                                style = MaterialTheme.typography.bodyMedium)
                            Text("RSSI: ${last.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
