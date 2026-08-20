package com.secureguard.enterprise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {

    @Query("SELECT * FROM detections WHERE assetMac = :mac ORDER BY timestamp DESC")
    fun observeForAsset(mac: String): Flow<List<Detection>>

    @Query("SELECT * FROM detections ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Detection>>

    @Query("SELECT * FROM detections WHERE assetMac = :mac ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestForAsset(mac: String): Detection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: Detection): Long

    @Query("DELETE FROM detections WHERE assetMac = :mac")
    suspend fun deleteForAsset(mac: String)

    @Query("DELETE FROM detections WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
