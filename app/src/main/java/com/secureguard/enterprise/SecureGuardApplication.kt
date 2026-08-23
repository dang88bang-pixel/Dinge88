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
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
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
        if (com.secureguard.enterprise.BuildConfig.DEBUG) {
            seedDemoDataIfEmpty()
        }
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

    /**
     * Populates the database with a handful of demo assets on first launch so
     * the dashboard, map and actions are not empty. The seed is idempotent.
     */
    private fun seedDemoDataIfEmpty() {
        appScope.launch {
            if (database.assetDao().count() > 0) return@launch
            val now = Date()
            val demo = listOf(
                Asset(
                    id = "asset-001",
                    name = "E-Scooter Roller #1",
                    shortName = "Roller #1",
                    mac = "AA:BB:CC:DD:EE:01",
                    status = AssetStatus.ONLINE,
                    rssi = -45,
                    batteryLevel = 78,
                    latitude = 52.5200,
                    longitude = 13.4050,
                    lastSeen = now,
                    externalAllowed = true
                ),
                Asset(
                    id = "asset-002",
                    name = "E-Bike Fahrrad #2",
                    shortName = "Fahrrad #2",
                    mac = "AA:BB:CC:DD:EE:02",
                    status = AssetStatus.MAINTENANCE,
                    rssi = -60,
                    batteryLevel = 54,
                    latitude = 52.4980,
                    longitude = 13.4040,
                    lastSeen = now,
                    maintenanceDue = true
                ),
                Asset(
                    id = "asset-003",
                    name = "Schlüsselfinder #3",
                    shortName = "Schlüssel #3",
                    mac = "AA:BB:CC:DD:EE:03",
                    status = AssetStatus.OFFLINE,
                    rssi = -90,
                    batteryLevel = 12,
                    lastSeen = Date(now.time - 2 * 60 * 60 * 1000L)
                ),
                Asset(
                    id = "asset-004",
                    name = "Tablet Wache #4",
                    shortName = "Tablet #4",
                    mac = "AA:BB:CC:DD:EE:04",
                    status = AssetStatus.ONLINE,
                    rssi = -55,
                    batteryLevel = 92,
                    latitude = 52.5219,
                    longitude = 13.4132,
                    lastSeen = now
                ),
                Asset(
                    id = "asset-005",
                    name = "Smartphone #5",
                    shortName = "Smartphone #5",
                    mac = "AA:BB:CC:DD:EE:05",
                    status = AssetStatus.ONLINE,
                    rssi = -50,
                    batteryLevel = 64,
                    latitude = 52.5380,
                    longitude = 13.4200,
                    lastSeen = now
                )
            )
            demo.forEach { database.assetDao().upsert(it) }
        }
    }
}
