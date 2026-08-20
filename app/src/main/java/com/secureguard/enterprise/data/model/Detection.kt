package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * A single sighting of an asset through one of the available channels.
 * Detections build the history shown on the asset detail screen and feed
 * the self-learning agent.
 */
@Entity(tableName = "detections")
data class Detection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetMac: String,
    val sourceType: DetectionSource,
    val nodeId: String? = null,
    val rssi: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val message: String? = null,
    val timestamp: Date = Date()
)
