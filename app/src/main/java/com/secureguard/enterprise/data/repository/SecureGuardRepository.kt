package com.secureguard.enterprise.data.repository

import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Single source of truth for persisted assets, detections and alerts.
 * All services and ViewModels talk to the database through this interface,
 * which keeps the GDPR audit log centralised.
 */
interface SecureGuardRepository {

    // ---- Assets ----
    fun getWhitelistedAssets(): Flow<List<Asset>>
    fun getAllAssets(): Flow<List<Asset>>
    suspend fun getAssetByMac(mac: String): Asset?
    suspend fun getAssetById(id: String): Asset?

    /** Resolves an asset either by its id or its MAC (navigation may pass either). */
    suspend fun resolveAsset(idOrMac: String): Asset?

    suspend fun upsertAsset(asset: Asset)
    suspend fun updateAssetStatus(
        mac: String,
        status: AssetStatus,
        timestamp: Long,
        rssi: Int? = null,
        lat: Double? = null,
        lon: Double? = null
    )
    suspend fun deleteAsset(id: String)

    // ---- Detections ----
    fun getDetections(mac: String): Flow<List<Detection>>
    suspend fun getLatestDetection(mac: String): Detection?
    suspend fun insertDetection(detection: Detection): Long

    // ---- Alerts ----
    fun getAlerts(): Flow<List<Alert>>
    fun getUnacknowledgedAlerts(): Flow<List<Alert>>
    fun getUnacknowledgedAlertCount(): Flow<Int>
    suspend fun insertAlert(alert: Alert): Long
    suspend fun acknowledgeAlert(id: Long)
    suspend fun acknowledgeAllAlerts()

    /** Convenience helper that builds and persists an alert. */
    suspend fun raiseAlert(
        assetId: String,
        type: AlertType,
        severity: AlertSeverity,
        message: String
    ): Long
}

class SecureGuardRepositoryImpl(
    private val assetDao: com.secureguard.enterprise.data.local.dao.AssetDao,
    private val detectionDao: com.secureguard.enterprise.data.local.dao.DetectionDao,
    private val alertDao: com.secureguard.enterprise.data.local.dao.AlertDao
) : SecureGuardRepository {

    override fun getWhitelistedAssets(): Flow<List<Asset>> = assetDao.observeWhitelisted()
    override fun getAllAssets(): Flow<List<Asset>> = assetDao.observeAll()
    override suspend fun getAssetByMac(mac: String): Asset? = assetDao.getByMac(mac)
    override suspend fun getAssetById(id: String): Asset? = assetDao.getById(id)

    override suspend fun resolveAsset(idOrMac: String): Asset? =
        assetDao.getByIdOrMac(idOrMac)

    override suspend fun upsertAsset(asset: Asset) = assetDao.upsert(asset)

    override suspend fun updateAssetStatus(
        mac: String,
        status: AssetStatus,
        timestamp: Long,
        rssi: Int?,
        lat: Double?,
        lon: Double?
    ) {
        val existing = assetDao.getByMac(mac)
        val date = Date(timestamp)
        if (existing != null) {
            assetDao.updateStatus(
                mac = mac,
                status = status,
                rssi = rssi ?: existing.rssi,
                lat = lat ?: existing.latitude,
                lon = lon ?: existing.longitude,
                timestamp = date
            )
        }
    }

    override suspend fun deleteAsset(id: String) = assetDao.deleteById(id)

    override fun getDetections(mac: String): Flow<List<Detection>> =
        detectionDao.observeForAsset(mac)

    override suspend fun getLatestDetection(mac: String): Detection? =
        detectionDao.latestForAsset(mac)

    override suspend fun insertDetection(detection: Detection): Long =
        detectionDao.insert(detection)

    override fun getAlerts(): Flow<List<Alert>> = alertDao.observeAll()
    override fun getUnacknowledgedAlerts(): Flow<List<Alert>> = alertDao.observeUnacknowledged()
    override fun getUnacknowledgedAlertCount(): Flow<Int> = alertDao.observeUnacknowledgedCount()
    override suspend fun insertAlert(alert: Alert): Long = alertDao.insert(alert)
    override suspend fun acknowledgeAlert(id: Long) = alertDao.acknowledge(id)
    override suspend fun acknowledgeAllAlerts() = alertDao.acknowledgeAll()

    override suspend fun raiseAlert(
        assetId: String,
        type: AlertType,
        severity: AlertSeverity,
        message: String
    ): Long = alertDao.insert(
        Alert(
            assetId = assetId,
            type = type,
            severity = severity,
            message = message,
            timestamp = Date()
        )
    )
}
