package com.secureguard.enterprise.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.secureguard.enterprise.data.model.Alert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE resolved = 0 ORDER BY timestamp DESC")
    fun getUnresolved(): Flow<List<Alert>>

    @Insert
    suspend fun insert(alert: Alert)

    @Update
    suspend fun update(alert: Alert)
}
