package com.secureguard.enterprise.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Ein überwachtes Firmen-Asset (Fahrzeug, Anlage, Gerät).
 */
@Entity(tableName = "assets")
@Parcelize
data class Asset(
    @PrimaryKey
    val id: String,
    val name: String,
    val mac: String,
    val vin: String? = null,
    val shortName: String,
    val icon: String,
    val status: AssetStatus,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rssi: Int = 0,
    val lastSeen: Date? = null,
    val alertSound: String = "laut",
    val vibration: Boolean = true,
    val whitelisted: Boolean = true,
    val externalAllowed: Boolean = false,
    val maintenanceInterval: Int = 10000
) : Parcelable

enum class AssetStatus { ONLINE, OFFLINE, MAINTENANCE, SEARCHING, UNKNOWN }
