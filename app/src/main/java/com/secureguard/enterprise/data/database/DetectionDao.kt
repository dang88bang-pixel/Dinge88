package com.secureguard.enterprise.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Query("SELECT * FROM detections WHERE assetMac = :mac ORDER BY timestamp DESC")
    fun getByAsset(mac: String): Flow<List<Detection>>

    @Insert
    suspend fun insert(detection: Detection)

    @Query("DELETE FROM detections WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
