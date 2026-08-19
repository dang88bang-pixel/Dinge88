package com.secureguard.enterprise.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * A protected, whitelisted asset (e-scooter, bicycle, key fob, tablet, ...).
 *
 * The device is tracked through multiple channels (BLE, WiFi, generic LoRa/LoRaWAN,
 * optical recognition, urban infrastructure, Apple/Google crowdsource networks and
 * satellite). Only assets present in this table are ever searched for, which keeps
 * the solution GDPR compliant.
 */
@Entity(tableName = "assets")
data class Asset(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String,
    val mac: String,
    val vin: String? = null,
    val status: AssetStatus = AssetStatus.UNKNOWN,
    val rssi: Int = 0,
    val batteryLevel: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastSeen: Date? = null,
    val whitelisted: Boolean = true,
    val externalAllowed: Boolean = false,
    val maintenanceDue: Boolean = false,
    val notes: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)
