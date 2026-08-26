package com.secureguard.enterprise

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.config.CT45PConfig
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.worker.SecureAgentWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SecureGuardApplication : Application(), Configuration.Provider {

    private companion object {
        const val TAG = "SecureGuard"
    }

    @Inject lateinit var database: SecureGuardDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backupManager: com.secureguard.enterprise.services.BackupManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Apply pending database restore if staged
        backupManager.applyPendingRestoreIfPresent()
        Log.i(
            TAG,
            "Gerät: ${CT45PConfig.deviceSummary()} · CT45P: ${CT45PConfig.isCT45P()} · " +
                "BLE braucht Standort (Android 11): ${CT45PConfig.needsLocationForBle}"
        )
        // Kein automatisches Seed mit Demo-/Kunstpositionen mehr. Assets werden
        // ausschließlich vom Nutzer oder über die REST-API angelegt.
        scheduleAgentWorker()
    }

    /**
     * Plant den Hintergrund-Agenten periodisch (15 Minuten). Bestehende
     * Planung bleibt erhalten (KEEP), damit der WorkManager nicht doppelt
     * läuft.
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
