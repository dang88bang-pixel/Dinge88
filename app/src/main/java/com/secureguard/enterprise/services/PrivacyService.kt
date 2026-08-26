package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DSGVO-Hilfen: Datenauskunft (Art. 15), Löschkonzept (Art. 17),
 * Retention-Bereinigung. Passwörter/PINs werden **nicht** exportiert.
 *
 * Produktions-PIN und Keystore-Passwörter legt der Anwender selbst fest.
 */
@Singleton
class PrivacyService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: SecureGuardDatabase,
    private val auditLogService: AuditLogService
) {

    data class DataSubjectExport(
        val generatedAt: String,
        val assetCount: Int,
        val detectionCount: Int,
        val alertCount: Int,
        val auditCount: Int,
        val file: File
    )

    data class PurgeResult(
        val assetsDeleted: Int = 0,
        val detectionsDeleted: Int = 0,
        val alertsDeleted: Int = 0,
        val auditDeleted: Int = 0,
        val pendingDeleted: Int = 0
    )

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /** Art. 15 – maschinenlesbare Datenauskunft (JSON, lokal). */
    suspend fun exportDataSubjectAccess(): DataSubjectExport = withContext(Dispatchers.IO) {
        val assets = database.assetDao().observeAll().first()
        val detections = database.detectionDao().observeAll().first()
        val alerts = database.alertDao().observeAll().first()
        val audit = database.auditLogDao().latest(5000)

        val root = JSONObject()
        root.put("exportType", "DSGVO_ART15_DATENAUSKUNFT")
        root.put("generatedAt", iso.format(Date()))
        root.put("note", "Keine Passwörter/PINs/Keystore-Secrets enthalten. Lokal erzeugte Auskunft.")

        root.put("assets", JSONArray().also { arr ->
            assets.forEach { a -> arr.put(assetJson(a)) }
        })
        root.put("detections", JSONArray().also { arr ->
            detections.forEach { d -> arr.put(detectionJson(d)) }
        })
        root.put("alerts", JSONArray().also { arr ->
            alerts.forEach { al ->
                arr.put(
                    JSONObject()
                        .put("id", al.id)
                        .put("assetId", al.assetId)
                        .put("type", al.type.name)
                        .put("severity", al.severity.name)
                        .put("message", al.message)
                        .put("acknowledged", al.acknowledged)
                        .put("timestamp", al.timestamp.time)
                )
            }
        })
        root.put("auditLog", JSONArray().also { arr ->
            audit.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("userId", e.userId)
                        .put("action", e.action)
                        .put("details", e.details)
                        .put("timestamp", e.timestamp)
                )
            }
        })

        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "privacy").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "datenauskunft_$stamp.json")
        file.writeText(root.toString(2))

        auditLogService.log("DSGVO_EXPORT", "Datenauskunft: ${file.name} (${assets.size} Assets)")
        DataSubjectExport(
            generatedAt = root.getString("generatedAt"),
            assetCount = assets.size,
            detectionCount = detections.size,
            alertCount = alerts.size,
            auditCount = audit.size,
            file = file
        )
    }

    /**
     * Retention: Detektionen/Alerts/Audit älter als [days] Tage löschen.
     * Assets bleiben erhalten.
     */
    suspend fun applyRetention(days: Int = DEFAULT_RETENTION_DAYS): PurgeResult =
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            val det = database.detectionDao().deleteOlderThan(cutoff)
            val al = database.alertDao().deleteOlderThan(cutoff)
            val au = database.auditLogDao().deleteOlderThan(cutoff)
            auditLogService.log(
                "DSGVO_RETENTION",
                "Retention ${days}d: detections=$det alerts=$al audit=$au"
            )
            PurgeResult(detectionsDeleted = det, alertsDeleted = al, auditDeleted = au)
        }

    /**
     * Art. 17 – vollständige lokale Löschung (Whitelist, Historie, Queue, Audit).
     * PIN/Auth-Prefs und DB-Key bleiben (Gerät bleibt nutzbar); optional separat.
     */
    suspend fun eraseAllLocalData(alsoClearAuth: Boolean = false): PurgeResult =
        withContext(Dispatchers.IO) {
            val assets = database.assetDao().count()
            val dets = database.detectionDao().count()
            database.detectionDao().clear()
            database.alertDao().clear()
            val auditN = database.auditLogDao().clear()
            database.pendingActionDao().clear()
            database.assetDao().clear()

            if (alsoClearAuth) {
                context.getSharedPreferences("secureguard_auth", Context.MODE_PRIVATE)
                    .edit().clear().apply()
            }

            // frischer Audit-Eintrag nach Wipe (wenn Auth bleibt)
            runCatching {
                auditLogService.log(
                    "DSGVO_ERASE",
                    "Lokale Daten gelöscht (assets≈$assets detections≈$dets authCleared=$alsoClearAuth)"
                )
            }

            PurgeResult(
                assetsDeleted = assets,
                detectionsDeleted = dets,
                alertsDeleted = -1,
                auditDeleted = auditN,
                pendingDeleted = -1
            )
        }

    private fun assetJson(a: Asset): JSONObject = JSONObject()
        .put("id", a.id)
        .put("name", a.name)
        .put("shortName", a.shortName)
        .put("mac", a.mac)
        .put("vin", a.vin)
        .put("status", a.status.name)
        .put("rssi", a.rssi)
        .put("batteryLevel", a.batteryLevel)
        .put("latitude", a.latitude)
        .put("longitude", a.longitude)
        .put("lastSeen", a.lastSeen?.time)
        .put("whitelisted", a.whitelisted)
        .put("externalAllowed", a.externalAllowed)
        .put("notes", a.notes)
        .put("createdAt", a.createdAt.time)
        .put("updatedAt", a.updatedAt.time)

    private fun detectionJson(d: Detection): JSONObject = JSONObject()
        .put("id", d.id)
        .put("assetMac", d.assetMac)
        .put("sourceType", d.sourceType.name)
        .put("nodeId", d.nodeId)
        .put("rssi", d.rssi)
        .put("latitude", d.latitude)
        .put("longitude", d.longitude)
        .put("message", d.message)
        .put("timestamp", d.timestamp.time)

    companion object {
        const val DEFAULT_RETENTION_DAYS = 90
    }
}
