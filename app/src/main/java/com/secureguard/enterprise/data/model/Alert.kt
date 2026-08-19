package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Ein Alarm / eine Warnung, die einem Asset zugeordnet ist.
 */
@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetId: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Date,
    val isAcknowledged: Boolean = false,
    val acknowledgedAt: Date? = null,
    val resolved: Boolean = false,
    val resolvedAt: Date? = null
)

enum class AlertType { MAINTENANCE, SECURITY, CRITICAL, INFO }
enum class AlertSeverity { INFO, WARNING, CRITICAL }
