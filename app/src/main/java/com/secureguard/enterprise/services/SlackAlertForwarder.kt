package com.secureguard.enterprise.services

import android.util.Log
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Meldet neue lokale Alerts automatisch an Slack.
 *
 * Der Forwarder beobachtet die Room-Tabelle `alerts` (ein einziger Hook für
 * **alle** Alert-Quellen: Agent-Zyklus, MQTT, NFC, Broadcast, USB-Serial) und
 * schickt neue Alarme ab [minSeverity] an das Backend (`POST /api/slack/notify`),
 * das sie über den Slack-MCP-Server in den Ziel-Channel postet.
 *
 * Doppelte Sicherheit gegen Spam:
 *  - nur Alerts **nach** dem Startzeitpunkt (kein Replay der Historie),
 *  - jede Alert-ID wird nur einmal gesendet,
 *  - ohne konfiguriertes Backend/Slack ist der Forwarder inaktiv.
 */
@Singleton
class SlackAlertForwarder @Inject constructor(
    private val database: SecureGuardDatabase,
    private val slackService: SlackService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val startedAt = System.currentTimeMillis()
    private val forwarded = ArrayDeque<Long>()
    private val forwardedIds = mutableSetOf<Long>()

    /** Ab dieser Schwere wird gemeldet (INFO bleibt lokal). */
    private val minSeverity: AlertSeverity = AlertSeverity.WARNING

    val isRunning: Boolean get() = job?.isActive == true

    /** Startet die Beobachtung (idempotent). */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        if (!slackService.isConfigured) {
            Log.i(TAG, "Kein Backend konfiguriert – Slack-Forwarder bleibt inaktiv")
            return
        }
        job = scope.launch {
            database.alertDao().observeAll()
                .catch { error -> Log.w(TAG, "Alert-Beobachtung beendet", error) }
                .collect { alerts -> alerts.forEach { maybeForward(it) } }
        }
        Log.i(TAG, "Slack-Forwarder gestartet (ab $minSeverity)")
    }

    /** Stoppt die Beobachtung (z. B. beim Beenden des Vordergrund-Diensts). */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun maybeForward(alert: Alert) {
        if (alert.id == 0L) return                       // noch nicht persistiert
        if (alert.timestamp.time < startedAt) return     // Historie überspringen
        if (alert.severity.ordinal < minSeverity.ordinal) return
        val isNew = synchronized(forwardedIds) {
            val added = forwardedIds.add(alert.id)
            if (added) {
                forwarded.addLast(alert.id)
                // Ringpuffer: nicht unbegrenzt IDs vorhalten
                while (forwarded.size > MAX_TRACKED_IDS) {
                    forwardedIds.remove(forwarded.removeFirst())
                }
            }
            added
        }
        if (!isNew) return
        val result = runCatching {
            slackService.notifyAlert(
                assetId = alert.assetId,
                alertType = alert.type.name,
                severity = alert.severity.name,
                message = alert.message
            )
        }.getOrNull()
        if (result?.ok == true) {
            Log.i(TAG, "Alert ${alert.id} → Slack (${result.channel})")
        } else {
            Log.w(
                TAG,
                "Alert ${alert.id} nicht an Slack gesendet: ${result?.detail ?: "kein Backend"}"
            )
        }
    }

    private companion object {
        const val TAG = "SlackAlertForwarder"
        const val MAX_TRACKED_IDS = 500
    }
}
