package com.secureguard.enterprise.ct45p

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeoutCancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CT45P-Fehlerbehandlung: protokolliert jeden Fehler in der on-device
 * Log-Datei (wo der Nutzer ihn jederzeit einsehen kann) und liefert
 * [CT45PErrorDialog] als sichtbaren Fehler-Dialog auf dem Bildschirm.
 */
@Singleton
class CT45PErrorHandler @Inject constructor(
    private val logManager: CT45PLogManager
) {

    fun handleError(throwable: Throwable, context: String) {
        logManager.logRequest(
            requestType = "ERROR",
            endpoint = context,
            parameters = emptyMap(),
            durationMs = 0,
            success = false,
            error = "${throwable.javaClass.simpleName}: ${throwable.message}"
        )
    }
}

/**
 * Fehler-Dialog auf dem CT45P-Bildschirm (z. B. "Verbindung zum
 * Zielgerät verloren"). Zeigt Ursache, Zeit, Fehlercode und Details;
 * Aktionen: Wiederholen / Log anzeigen / OK.
 */
@Composable
fun CT45PErrorDialog(
    throwable: Throwable,
    context: String = "Anfrage",
    targetDevice: String? = null,
    source: String? = null,
    attempts: Int = 3,
    onRetry: () -> Unit,
    onShowLog: () -> Unit,
    onDismiss: () -> Unit
) {
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    val durationMs = 5000L
    val errorCode = when (throwable) {
        is TimeoutCancellationException -> "ERR_TIMEOUT"
        is SocketTimeoutException -> "ERR_TIMEOUT"
        else -> "ERR_${throwable.javaClass.simpleName.replace(Regex("[^A-Z_]"), "").take(20)}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ VERBINDUNGSFEHLER") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("❌ Fehler beim Verarbeiten von $context")
                Text("Ursache: ${throwable.message ?: "Unbekannter Fehler"}")
                Text("Zeit: $time")
                Text("Dauer: ${durationMs / 1000.0}s")
                Text("Fehlercode: $errorCode")
                if (targetDevice != null) Text("• Gerät: $targetDevice")
                if (source != null) Text("• Quelle: $source")
                Text("• Versuche: $attempts/3")
            }
        },
        confirmButton = {
            TextButton(onClick = onRetry) { Text("🔄 WIEDERHOLEN") }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onShowLog) { Text("📋 LOG ANZEIGEN") }
                TextButton(onClick = onDismiss) {
                    Text("✅ OK", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    )
}
