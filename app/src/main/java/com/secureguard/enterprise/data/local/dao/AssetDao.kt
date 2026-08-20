package com.secureguard.enterprise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM assets WHERE whitelisted = 1 ORDER BY shortName COLLATE NOCASE ASC")
    fun observeWhitelisted(): Flow<List<Asset>>

    @Query("SELECT * FROM assets ORDER BY shortName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Asset?

    @Query("SELECT * FROM assets WHERE mac = :mac COLLATE NOCASE LIMIT 1")
    suspend fun getByMac(mac: String): Asset?

    @Query("SELECT * FROM assets WHERE id = :id OR mac = :id COLLATE NOCASE LIMIT 1")
    suspend fun getByIdOrMac(id: String): Asset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: Asset)

    @Update
    suspend fun update(asset: Asset)

    @Query(
        """
        UPDATE assets
        SET status = :status,
            rssi = :rssi,
            latitude = COALESCE(:lat, latitude),
            longitude = COALESCE(:lon, longitude),
            lastSeen = :timestamp,
            updatedAt = :timestamp
        WHERE mac = :mac COLLATE NOCASE
        """
    )
    suspend fun updateStatus(
        mac: String,
        status: AssetStatus,
        rssi: Int,
        lat: Double?,
        lon: Double?,
        timestamp: java.util.Date
    )

    @Query("UPDATE assets SET status = :status, updatedAt = :timestamp WHERE mac = :mac COLLATE NOCASE")
    suspend fun setStatus(mac: String, status: AssetStatus, timestamp: java.util.Date)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM assets")
    suspend fun count(): Int
}
