package com.secureguard.enterprise.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.secureguard.enterprise.data.model.PendingAction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingAction>

    @Query("SELECT COUNT(*) FROM pending_actions")
    suspend fun count(): Int

    @Insert
    suspend fun insert(action: PendingAction): Long

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_actions SET attempts = attempts + 1, lastError = :error WHERE id = :id")
    suspend fun markAttempt(id: Long, error: String?)
}
