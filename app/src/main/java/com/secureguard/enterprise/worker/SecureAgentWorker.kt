package com.secureguard.enterprise.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secureguard.enterprise.presentation.ui.common.requiredPermissions
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hintergrund-Agent (WorkManager): führt einmalig einen kompletten
 * Suchzyklus aus, auch wenn die App nicht im Vordergrund ist.
 * Wird in der Application periodisch (15 Min) geplant.
 * Prüft vor jedem Zyklus die notwendigen Berechtigungen.
 */
@HiltWorker
class SecureAgentWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val agentService: AgentService,
    private val notificationService: NotificationService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Berechtigungsprüfung vor jedem Zyklus (100% Funktionssicherheit)
            val missing = requiredPermissions().filter {
                ContextCompat.checkSelfPermission(applicationContext, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                notificationService.sendAgentCycleNotification(
                    "⚠️ Agent pausiert – fehlende Berechtigungen: ${missing.size}"
                )
                return Result.failure()
            }

            val result = agentService.runCycle()
            notificationService.sendAgentCycleNotification(
                "Zyklus abgeschlossen · ${result.assetsChecked} Assets · ${result.detections} Treffer"
            )
            Result.success()
        } catch (e: Exception) {
            notificationService.sendAgentCycleNotification("Agent-Fehler: ${e.message}")
            Result.retry()
        }
    }
}
