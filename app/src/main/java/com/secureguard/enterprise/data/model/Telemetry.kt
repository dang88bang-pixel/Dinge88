package com.secureguard.enterprise.data.model

import java.util.Date

/**
 * Telemetry payload reported by an asset (battery, fuel, motor health, ...).
 * Telemetry is not persisted as a separate table in this version; the latest
 * reading is held in memory by [com.secureguard.enterprise.services.TelemetryService]
 * and the most important fields are mirrored onto the [Asset] itself.
 */
data class Telemetry(
    val mac: String,
    val batteryPercent: Int? = null,
    val fuelPercent: Int? = null,
    val motorOk: Boolean = true,
    val tiresOk: Boolean = true,
    val operatingHours: Double? = null,
    val kilometers: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Date = Date()
)
