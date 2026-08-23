package com.secureguard.enterprise

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.config.CT45PConfig
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.BackupManager
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.WebSocketService
import com.secureguard.enterprise.worker.MaintenanceWorker
import com.secureguard.enterprise.worker.SecureAgentWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class SecureGuardApplication : Application(), Configuration.Provider {

    private companion object {
        const val TAG = "SecureGuard"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var agentService: AgentService
    @Inject lateinit var mqttService: MqttService
    @Inject lateinit var webSocketService: WebSocketService
    @Inject lateinit var backupManager: BackupManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(
            TAG,
            "Gerät: ${CT45PConfig.deviceSummary()} · CT45P: ${CT45PConfig.isCT45P()} · " +
                "BLE braucht Standort (Android 11): ${CT45PConfig.needsLocationForBle}"
        )
        applyPendingBackupRestore()
        scheduleAgentWorker()
        scheduleMaintenanceWorker()
        registerConnectivityWatcher()
    }

    /**
     * Plant den Hintergrund-Agenten periodisch (15 Minuten). Bestehende
     * Planung bleibt erhalten (KEEP), damit der WorkManager nicht doppelt
     * läuft. Nach Boot/Update übernimmt [com.secureguard.enterprise.receiver.BootCompletedReceiver]
     * die Neuplanung.
     */
    private fun scheduleAgentWorker() {
        val request = PeriodicWorkRequestBuilder<SecureAgentWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "secureguard_agent_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Plant den Wartungs-Worker (täglich): Retention-Bereinigung der
     * Datenbank + Nachlieferung der Offline-Queue.
     */
    private fun scheduleMaintenanceWorker() {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "secureguard_maintenance_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Konnektivitäts-Überwachung: sobald wieder Internet verfügbar ist,
     * werden (a) wartende Offline-Aktionen nachgeliefert und (b) die
     * Echtzeit-Kanäle (MQTT/WebSocket) neu verbunden.
     */
    private fun registerConnectivityWatcher() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Netzwerk verfügbar – Offline-Queue flushen, Echtzeitkanäle verbinden")
                appScope.launch {
                    runCatching { agentService.flushOfflineQueue() }
                }
                runCatching { mqttService.connect() }
                if (webSocketService.isConfigured) {
                    runCatching { webSocketService.connect() }
                }
            }
        })
    }

    /**
     * Wendet ein vorher bereitgestelltes Backup an (Restore wird in zwei
     * Phasen ausgeführt, da Room nur bei geschlossener DB konsistent ist).
     */
    private fun applyPendingBackupRestore() {
        appScope.launch {
            runCatching { backupManager.applyPendingRestoreIfPresent() }
                .onFailure { Log.w(TAG, "Backup-Restore nicht anwendbar: ${it.message}") }
        }
    }
}
