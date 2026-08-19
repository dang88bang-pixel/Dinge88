package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Eine einzelne Erkennung (Detection) eines Assets über eine Ortungsquelle.
 */
@Entity(tableName = "detections")
data class Detection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetMac: String,
    val sourceType: DetectionSource,
    val nodeId: String,
    val rssi: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Date,
    val isVerified: Boolean = false,
    val triangulationPoints: Int = 1
)

enum class DetectionSource {
    BLE, WIFI, LORA, NFC, GPS, OPTICAL, URBAN, CROWD, SATELLITE, UNKNOWN
}
