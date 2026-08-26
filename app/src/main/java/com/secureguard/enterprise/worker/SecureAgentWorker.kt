package com.secureguard.enterprise.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Hintergrund-Agent (WorkManager): führt einmalig einen kompletten
 * Suchzyklus aus, auch wenn die App nicht im Vordergrund ist.
 * Wird in der Application periodisch (15 Min) geplant.
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
            val result = agentService.runCycle()
            notificationService.sendAgentCycleNotification(
                "Zyklus abgeschlossen · ${result.assetsChecked} Assets · ${result.detections} Treffer"
            )
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
