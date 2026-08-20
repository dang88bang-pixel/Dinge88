package com.secureguard.enterprise.util

import android.content.Context
import android.util.Log
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Globaler Error Handler: loggt Fehler in Logcat und ins Audit-Log und
 * meldet sie optional an einen Listener (z. B. für eine UI-Benachrichtigung).
 */
@Singleton
class ErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auditLogService: AuditLogService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var onError: ((operation: String, message: String) -> Unit)? = null

    fun handleError(throwable: Throwable, operation: String) {
        Log.e(TAG, "Fehler bei $operation: ${throwable.message}", throwable)
        scope.launch {
            runCatching {
                auditLogService.log(
                    action = "ERROR",
                    details = "$operation: ${throwable.message ?: throwable.javaClass.simpleName}"
                )
            }
        }
        onError?.invoke(operation, throwable.message ?: "Unbekannter Fehler")
    }

    companion object {
        private const val TAG = "SecureGuard"
    }
}
