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
        // osmdroid verlangt einen User-Agent (Pflicht), sonst wirft er eine Exception.
        OsmdroidConfiguration.getInstance().userAgentValue =
            "SecureGuard-Enterprise/${BuildConfig.VERSION_NAME}"

        scheduleAgentWorker()
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
