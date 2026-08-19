package com.secureguard.enterprise.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets")
    fun getAll(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE whitelisted = 1")
    fun getWhitelisted(): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE mac = :mac")
    suspend fun getByMac(mac: String): Asset?

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): Asset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: Asset)

    @Update
    suspend fun update(asset: Asset)

    @Delete
    suspend fun delete(asset: Asset)

    @Query(
        "UPDATE assets SET status = :status, lastSeen = :timestamp, " +
            "latitude = :lat, longitude = :lon WHERE mac = :mac"
    )
    suspend fun updateStatus(
        mac: String,
        status: AssetStatus,
        timestamp: Long,
        lat: Double?,
        lon: Double?
    )
}
