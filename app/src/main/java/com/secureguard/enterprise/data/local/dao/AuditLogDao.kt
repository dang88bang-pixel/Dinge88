package com.secureguard.enterprise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secureguard.enterprise.data.model.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun latest(limit: Int = 100): List<AuditLog>

    @Insert
    suspend fun insert(entry: AuditLog): Long

    @Query("DELETE FROM audit_log WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM audit_log")
    suspend fun clear(): Int
}
