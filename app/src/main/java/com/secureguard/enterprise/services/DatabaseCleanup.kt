package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.local.SecureGuardDatabase
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Datenbereinigung: entfernt alte Detektionen, Alarme und Audit-Log-Einträge
 * (Retention), damit die lokale Datenbank nicht unbegrenzt wächst.
 */
@Singleton
class DatabaseCleanup @Inject constructor(
    private val database: SecureGuardDatabase
) {

    /**
     * Führt die Bereinigung aus.
     * @param detectionRetentionDays Aufbewahrung für Detektionen (Standard: 30)
     * @param alertRetentionDays     Aufbewahrung für Alarme (Standard: 90)
     * @param auditRetentionDays     Aufbewahrung für das Audit-Log (Standard: 365)
     */
    suspend fun cleanup(
        detectionRetentionDays: Long = 30,
        alertRetentionDays: Long = 90,
        auditRetentionDays: Long = 365
    ): CleanupResult {
        val now = System.currentTimeMillis()
        val detections = database.detectionDao().deleteOlderThan(
            now - TimeUnit.DAYS.toMillis(detectionRetentionDays)
        )
        val alerts = database.alertDao().deleteOlderThan(
            now - TimeUnit.DAYS.toMillis(alertRetentionDays)
        )
        val audit = database.auditLogDao().deleteOlderThan(
            now - TimeUnit.DAYS.toMillis(auditRetentionDays)
        )
        return CleanupResult(detections, alerts, audit)
    }

    /** Anzahl der aktuell gespeicherten Detektionen (für Anzeige/Tests). */
    suspend fun detectionCount(): Int =
        // F-61i: COUNT(*) statt observeAll().first() – sonst wird die ganze
        // detections-Tabelle materialisiert, nur um sie zu zählen.
        database.detectionDao().count()
}

data class CleanupResult(
    val deletedDetections: Int = 0,
    val deletedAlerts: Int = 0,
    val deletedAuditEntries: Int = 0
)
