package com.secureguard.enterprise.services

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin foreground wrapper around [AgentService]. Starting this service promotes
 * the agent to a foreground state (so Android does not kill it while the screen
 * is off) and keeps a persistent status notification.
 *
 * Android-11…14-Verhalten (API 30–34):
 * - API 29+: der FGS-Typ wird explizit an `startForeground` übergeben.
 * - API 34 (Android 14): jeder verwendete Typ braucht die passende
 *   `FOREGROUND_SERVICE_<TYP>`-Permission (Manifest) **und** für `location`
 *   eine zur Laufzeit erteilte Standortberechtigung – sonst wirft
 *   `startForeground` eine `SecurityException`. Deshalb wird `location` nur
 *   angehängt, wenn Fine/Coarse-Location gewährt ist; `dataSync` (MQTT/WS/Sync)
 *   ist immer dabei.
 * - Schlägt `startForeground` dennoch fehl (z. B. Start aus dem Hintergrund
 *   auf Android 12+), beendet sich der Dienst sauber statt die App zu crashen;
 *   der 15-Minuten-WorkManager-Zyklus läuft unabhängig weiter.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var agentService: AgentService
    @Inject lateinit var notificationService: NotificationService
    @Inject lateinit var agentSettingsStore: AgentSettingsStore

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                agentService.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = notificationService.buildAgentNotification("Agent wird initialisiert …")
        val promoted = runCatching { startForegroundCompat(notification) }
            .onFailure { Log.e(TAG, "startForeground fehlgeschlagen – Dienst wird beendet", it) }
            .isSuccess
        if (!promoted) {
            // Kein Vordergrund-Status möglich (Android 12+ Hintergrundstart,
            // fehlende Berechtigung o. ä.): nicht als „unsichtbarer" Dienst
            // weiterlaufen, sondern sauber beenden.
            stopSelf()
            return START_NOT_STICKY
        }

        // Persistierte Agent-Einstellungen (Agent-Config-Screen) statt Defaults –
        // gleicher Startpfad wie Dashboard/Worker.
        agentService.start(agentSettingsStore.load())
        return START_STICKY
    }

    override fun onDestroy() {
        agentService.stop()
        super.onDestroy()
    }

    /**
     * `startForeground` mit API-Level-gerechtem FGS-Typ.
     * `location` nur mit Laufzeit-Standortberechtigung (Android-14-Pflicht),
     * Fallback auf reinen `dataSync`, falls das System den Typ ablehnt.
     */
    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(NotificationService.AGENT_NOTIFICATION_ID, notification)
            return
        }
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        if (hasLocationPermission()) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        try {
            ServiceCompat.startForeground(
                this,
                NotificationService.AGENT_NOTIFICATION_ID,
                notification,
                type
            )
        } catch (e: SecurityException) {
            if (type == ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) throw e
            // Standortberechtigung zwischen Prüfung und Start entzogen oder
            // Typ vom System abgelehnt → ohne location weiterlaufen.
            Log.w(TAG, "FGS-Typ location abgelehnt – Fallback auf dataSync", e)
            ServiceCompat.startForeground(
                this,
                NotificationService.AGENT_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val TAG = "AgentFGS"
        const val ACTION_START = "com.secureguard.enterprise.AGENT_START"
        const val ACTION_STOP = "com.secureguard.enterprise.AGENT_STOP"
    }
}
