package com.secureguard.enterprise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.secureguard.enterprise.data.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE acknowledged = 0 ORDER BY timestamp DESC")
    fun observeUnacknowledged(): Flow<List<Alert>>

    @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
    fun observeUnacknowledgedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: Alert): Long

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("UPDATE alerts SET acknowledged = 1")
    suspend fun acknowledgeAll()

    @Query("DELETE FROM alerts")
    suspend fun clear()
}
