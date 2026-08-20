package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit-Log-Eintrag: dokumentiert wer (bzw. welche Komponente) welche Aktion
 * wann ausgeführt hat – Grundlage für das Berechtigungs- und
 * Nachvollziehbarkeits-Konzept (RBAC).
 */
@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String = "system",
    val action: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String? = null,
    val ipAddress: String? = null
)
