package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/** An alert / event stored in the local audit log. */
@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val acknowledged: Boolean = false,
    val timestamp: Date = Date()
)
