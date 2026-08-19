package com.secureguard.enterprise

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.worker.SecureAgentWorker
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmdroidConfiguration
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SecureGuardApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        scheduleAgentWorker()
    }

    /**
     * osmdroid auf Android 11 (scoped storage): nur App-privates Verzeichnis,
     * sonst schlägt der Kachel-Cache fehl.
     */
    private fun configureOsmdroid() {
        val base = File(filesDir, "osmdroid").apply { mkdirs() }
        val tiles = File(base, "tiles").apply { mkdirs() }
        val cfg = OsmdroidConfiguration.getInstance()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = tiles
        cfg.userAgentValue = "SecureGuard-Enterprise/${BuildConfig.VERSION_NAME}"
        cfg.load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
    }

    /**
     * Plant den Hintergrund-Agenten periodisch (WorkManager).
     * Der Worker führt einen vollständigen Suchzyklus aus, auch wenn die App
     * nicht im Vordergrund ist.
     */
    private fun scheduleAgentWorker() {
        val request = PeriodicWorkRequestBuilder<SecureAgentWorker>(
            15, TimeUnit.MINUTES
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SecureAgentWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
