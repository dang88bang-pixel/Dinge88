package com.secureguard.enterprise.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.secureguard.enterprise.worker.SecureAgentWorker
import java.util.concurrent.TimeUnit

/**
 * Restarts the periodic agent worker after device reboot.
 * Registered for RECEIVE_BOOT_COMPLETED in AndroidManifest.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Bewusst KEIN LOCKED_BOOT_COMPLETED: Vor dem User-Unlock ist die
        // Credential-Encrypted-Storage nicht verfügbar; WorkManager/Hilt/Room
        // (alle CE-Storage) würden dort crashen. Der periodische Worker wird
        // ohnehin bei Application.onCreate neu geplant.
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot abgeschlossen – Agent-Worker wird neu geplant")

        runCatching {
            val request = PeriodicWorkRequestBuilder<SecureAgentWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "secureguard_agent_worker",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }.onFailure {
            Log.w(TAG, "WorkManager nach Boot nicht verfügbar", it)
        }
    }

    companion object {
        private const val TAG = "SecureGuardBoot"
    }
}
