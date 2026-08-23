package com.secureguard.enterprise.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.DatabaseCleanup
import com.secureguard.enterprise.services.NotificationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Wartungs-Worker (täglich): führt die Retention-Bereinigung der lokalen
 * Datenbank aus (Detektionen 30 T., Alarme 90 T., Audit-Log 365 T. —
 * siehe [DatabaseCleanup]) und liefert danach wartende Offline-Aktionen
 * nach ([AgentService.flushOfflineQueue]).
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val databaseCleanup: DatabaseCleanup,
    private val agentService: AgentService,
    private val notificationService: NotificationService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cleanup = databaseCleanup.cleanup()
            val deliveredActions = agentService.flushOfflineQueue()

            notificationService.sendAgentCycleNotification(
                "Wartung: ${cleanup.deletedDetections} alte Detektionen, " +
                    "${cleanup.deletedAlerts} Alarme, " +
                    "${cleanup.deletedAuditEntries} Audit-Einträge gelöscht · " +
                    "$deliveredActions Offline-Aktionen zugestellt"
            )
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
