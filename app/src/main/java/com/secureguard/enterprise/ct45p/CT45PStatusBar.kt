package com.secureguard.enterprise.ct45p

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CT45P-Statusleiste (obere Statusleiste auf dem Gerät):
 *
 *  - ● AKTIV/INAKTIV + Knoten + Batterie + Zeit
 *  - Letztere Aktion / letzte Anfrage (inkl. Dauer)
 *  - Echtzeit-Aktivitätslog (letzte 4 Einträge)
 *  - 24h-Statistik (erfolgreich/fehlgeschlagen, Quellen, Ø Zeit)
 *
 * [onOpenLog] öffnet optional den vollständigen Aktivitätsverlauf.
 */
@Composable
fun CT45PStatusBar(
    viewModel: CT45PStatusViewModel = hiltViewModel(),
    onOpenLog: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val batteryLevel = rememberBatteryLevel(context)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.agentRunning) {
                Color.Green.copy(alpha = 0.08f)
            } else {
                Color.Red.copy(alpha = 0.08f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Erste Zeile: Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (uiState.agentRunning) Color.Green else Color.Red,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.agentRunning) "AKTIV" else "INAKTIV",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.agentRunning) Color.Green else Color.Red
                    )
                }
                Text(
                    "📶 ${uiState.activeNodes} Knoten",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "🔋 $batteryLevel%",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "⏱ ${uiState.lastActionTime}",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Zweite Zeile: Letzte Aktion
            Text(
                text = "📊 Letzte Aktion: ${uiState.lastAction} → ${uiState.lastActionTarget} " +
                    "(${if (uiState.lastActionSuccess) "✅ Erfolg" else "❌ Fehler"})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dritte Zeile: Letzte Anfrage
            Text(
                text = "📡 Letzte Anfrage: ${uiState.lastRequestSource} (${uiState.lastRequestDuration})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Echtzeit-Aktivitätslog
            if (uiState.recentEntries.isNotEmpty()) {
                Text(
                    "📋 AKTIVITÄTS-LOG (Echtzeit)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                uiState.recentEntries.take(4).forEach { entry ->
                    Text(
                        text = "${formatTime(entry.timestamp)}  ${entry.action}" +
                            (if (entry.details.isNotEmpty()) "  → ${entry.details.take(48)}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 24h-Statistik
            if (uiState.stats.successCount + uiState.stats.errorCount > 0) {
                val sourceText = uiState.stats.bySource.entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .joinToString(", ") { "${it.key} (${it.value})" }
                Text(
                    text = "📊 STATISTIK (24h): ✅ ${uiState.stats.successCount} · " +
                        "❌ ${uiState.stats.errorCount} · 📡 ${sourceText.ifEmpty { "–" }} · " +
                        "⏱ Ø ${(uiState.stats.averageDurationMs / 1000.0)}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (onOpenLog != null) {
                Spacer(modifier = Modifier.height(2.dp))
                TextButton(onClick = onOpenLog) {
                    Text("📋 Vollständiges Log anzeigen →")
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

/** Echte Batterieladung des Geräts (sticky Intent, kein Receiver nötig). */
@Composable
private fun rememberBatteryLevel(context: Context): Int {
    return try {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else 100
    } catch (e: Exception) {
        100
    }
}
