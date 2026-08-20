package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Offline-Queue-Eintrag: eine Aktion (z. B. ALARM, MOTOR_OFF, MESSAGE), die
 * bei fehlender Verbindung persistiert und später erneut zugestellt wird.
 * [payload] enthält die JSON-Parameter der Aktion.
 */
@Entity(tableName = "pending_actions")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String,
    val assetMac: String,
    val payload: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastError: String? = null
)
