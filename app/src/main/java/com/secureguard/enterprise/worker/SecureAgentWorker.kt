package com.secureguard.enterprise.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.secureguard.enterprise.services.AgentService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodischer Worker, der einen kompletten Agent-Suchzyklus ausführt
 * (WorkManager).
 */
@HiltWorker
class SecureAgentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val agentService: AgentService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            agentService.runCycleOnce()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "secureguard_agent_worker"
    }
}
