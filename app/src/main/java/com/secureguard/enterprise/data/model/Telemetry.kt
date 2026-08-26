package com.secureguard.enterprise.data.model

import java.util.Date

/**
 * Telemetry payload reported by an asset (battery, fuel, motor health, ...).
 * Telemetry is not persisted as a separate table in this version; the latest
 * reading is held in memory by [com.secureguard.enterprise.services.TelemetryService]
 * and the most important fields are mirrored onto the [Asset] itself.
 *
 * Schema-Abstimmung mit der ESP32-Firmware (secureguard_esp32.ino):
 * Die Firmware liefert `battery`, `motor`, `wifi_rssi`, `lora_rssi`,
 * `uptime`, `ip`, `device`. Keys ohne Sensor-Hardware (fuel, tires,
 * hours, km, lat, lon) bleiben `null` und werden von der Firmware nicht
 * gefälscht.
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
    /** WiFi-Signalstärke am Gerät (dBm), so von der Firmware gemeldet. */
    val wifiRssi: Int? = null,
    /** LoRa-Signalstärke am Gateway (dBm), so von der Firmware gemeldet. */
    val loraRssi: Int? = null,
    /** Uptime des Geräts in Sekunden. */
    val uptimeSeconds: Long? = null,
    /** IP-Adresse im lokalen Netz (Firmware-Meldung). */
    val ipAddress: String? = null,
    /** Geräte-Identifikator der Firmware. */
    val device: String? = null,
    val timestamp: Date = Date()
)
