package com.secureguard.enterprise.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.worker.MaintenanceWorker
import com.secureguard.enterprise.worker.SecureAgentWorker
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

/**
 * Neustart-Empfänger (BOOT_COMPLETED / MY_PACKAGE_REPLACED):
 *
 * 1. Plant die periodischen Worker neu (WorkManager tut dies zwar selbst,
 *    aber nach App-Updates und Werksresets wird die Planung garantiert
 *    frisch aufgesetzt – auch für den neuen Maintenance-Worker).
 * 2. Nimmt den Agent-Vordergrunddienst wieder auf, wenn er vor dem
 *    Reboot/Update lief (Flag „agent_autostart" aus den Einstellungen).
 *
 * Scheitert der FGS-Start (Android-14/15-Hintergrundbeschränkungen), fällt
 * das System auf den periodischen WorkManager-Zyklus zurück – der Agent
 * läuft dann im 15-Min-Takt weiter.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        Log.i(TAG, "Systemstart/Update erkannt ($action) – Worker neu planen")

        val workManager = WorkManager.getInstance(context)

        // 1. Such-Agent (15 Minuten)
        workManager.enqueueUniquePeriodicWork(
            "secureguard_agent_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SecureAgentWorker>(15, TimeUnit.MINUTES).build()
        )

        // 2. Datenbereinigung (täglich, Retention)
        workManager.enqueueUniquePeriodicWork(
            "secureguard_maintenance_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        )

        // 3. Agent-Vordergrunddienst wieder aufnehmen, falls aktiv gewesen.
        val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AGENT_AUTOSTART, false)) {
            try {
                context.startForegroundService(
                    Intent(context, AgentForegroundService::class.java)
                )
                Log.i(TAG, "Agent-Vordergrunddienst nach Neustart wieder aufgenommen")
            } catch (e: Exception) {
                // Android 14+/15: FGS-Start im Hintergrund ggf. verboten –
                // der periodische Worker übernimmt ab hier.
                Log.w(
                    TAG,
                    "FGS-Start nach Boot nicht erlaubt (${e.javaClass.simpleName}) – " +
                        "Agent läuft über den WorkManager-Zyklus weiter"
                )
            }
        }
    }

    companion object {
        private const val TAG = "SecureGuard"
        const val KEY_AGENT_AUTOSTART = "agent_autostart"
    }
}
