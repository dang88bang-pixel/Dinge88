package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.AuditLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audit-Log: persistiert sicherheitsrelevante Aktionen („Wer hat was wann
 * gemacht?"). Wird vom Agenten und den Aktionen aufgerufen; die Historie
 * lässt sich über [entries] in der UI anzeigen.
 */
@Singleton
class AuditLogService @Inject constructor(
    private val database: SecureGuardDatabase
) {

    val entries: Flow<List<AuditLog>> = database.auditLogDao().observeAll()

    suspend fun log(
        action: String,
        details: String = "",
        userId: String = "system",
        deviceId: String? = null,
        ipAddress: String? = null
    ) {
        database.auditLogDao().insert(
            AuditLog(
                userId = userId,
                action = action,
                details = details,
                deviceId = deviceId,
                ipAddress = ipAddress
            )
        )
    }

    suspend fun latest(limit: Int = 100): List<AuditLog> =
        database.auditLogDao().latest(limit)
}
