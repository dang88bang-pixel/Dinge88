package com.secureguard.enterprise.data.repository

import com.secureguard.enterprise.data.database.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentraler Datenzugriff auf die lokale Room-Datenbank. Keine Cloud-Abhängigkeit.
 */
@Singleton
class SecureGuardRepository @Inject constructor(
    private val database: SecureGuardDatabase
) {
    // --- Assets ---
    fun getAllAssets(): Flow<List<Asset>> = database.assetDao().getAll()
    fun getWhitelistedAssets(): Flow<List<Asset>> = database.assetDao().getWhitelisted()
    suspend fun getAssetByMac(mac: String): Asset? = database.assetDao().getByMac(mac)
    suspend fun insertAsset(asset: Asset) = database.assetDao().insert(asset)
    suspend fun updateAsset(asset: Asset) = database.assetDao().update(asset)
    suspend fun deleteAsset(asset: Asset) = database.assetDao().delete(asset)
    suspend fun updateAssetStatus(
        mac: String,
        status: AssetStatus,
        timestamp: Long,
        lat: Double?,
        lon: Double?
    ) = database.assetDao().updateStatus(mac, status, timestamp, lat, lon)

    // --- Detections ---
    fun getDetections(mac: String): Flow<List<Detection>> = database.detectionDao().getByAsset(mac)
    suspend fun insertDetection(detection: Detection) = database.detectionDao().insert(detection)

    // --- Alerts ---
    fun getUnresolvedAlerts(): Flow<List<Alert>> = database.alertDao().getUnresolved()
    fun getAllAlerts(): Flow<List<Alert>> = database.alertDao().getAll()
    suspend fun insertAlert(alert: Alert) = database.alertDao().insert(alert)
    suspend fun updateAlert(alert: Alert) = database.alertDao().update(alert)
}
