package com.secureguard.enterprise

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.config.CT45PConfig
import com.secureguard.enterprise.worker.SecureAgentWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SecureGuardApplication : Application(), Configuration.Provider {

    private companion object {
        const val TAG = "SecureGuard"
    }

    @Inject lateinit var workerFactory: HiltWorkerFactory

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
        scheduleAgentWorker()
    }

    /**
     * Plant den Hintergrund-Agenten periodisch (15 Minuten). Bestehende
     * Planung bleibt erhalten (KEEP), damit der WorkManager nicht doppelt
     * läuft.
     *
     * Hinweis: Der frühere automatische Demo-Daten-Seed wurde entfernt.
     * Die App startet mit einer leeren, echten Datenbank; Demo-Daten können
     * explizit über die Einstellungen geladen werden (DemoDataManager).
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
}
