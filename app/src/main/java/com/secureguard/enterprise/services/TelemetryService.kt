package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Reads telemetry (battery, fuel, motor health, location, ...) from the asset
 * over BLE / GATT and dispatches commands back to it.
 *
 * The hardware integration is abstracted behind [fetchTelemetry] and
 * [dispatchCommand]; the defaults simulate a device so the app runs without
 * physical hardware. Replace those methods to talk to a real GATT profile.
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val latest = mutableMapOf<String, Telemetry>()
    private val mutex = Mutex()

    suspend fun searchAsset(asset: Asset): Detection? {
        val telemetry = getLatestTelemetry(asset.mac) ?: fetchTelemetry(asset)
        if (telemetry != null) {
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.TELEMETRY,
                nodeId = "telemetry-gatt",
                rssi = -40 - Random.nextInt(0, 30),
                latitude = telemetry.latitude,
                longitude = telemetry.longitude,
                accuracyMeters = 8f,
                timestamp = telemetry.timestamp
            ).also { emit(it) }
        }
        return null
    }

    suspend fun getLatestTelemetry(mac: String): Telemetry? = mutex.withLock {
        latest[mac.uppercase()]
    }

    /** Simulates a GATT read and caches the result. */
    private suspend fun fetchTelemetry(asset: Asset): Telemetry? {
        delay(150)
        val telemetry = Telemetry(
            mac = asset.mac,
            batteryPercent = asset.batteryLevel ?: (60 + Random.nextInt(0, 40)),
            fuelPercent = 45,
            motorOk = true,
            tiresOk = true,
            operatingHours = 12_456.0 + Random.nextDouble(0.0, 5.0),
            kilometers = 234_567.0 + Random.nextDouble(0.0, 2.0),
            latitude = asset.latitude ?: 52.5200 + Random.nextDouble(-0.01, 0.01),
            longitude = asset.longitude ?: 13.4050 + Random.nextDouble(-0.01, 0.01),
            timestamp = Date()
        )
        mutex.withLock { latest[asset.mac.uppercase()] = telemetry }
        return telemetry
    }

    /** Sends a command string to the asset. Returns whether delivery succeeded. */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return dispatchCommand(mac, command)
    }

    protected open suspend fun dispatchCommand(mac: String, command: String): Boolean {
        delay(120)
        // Simulated delivery; real implementation would write to a GATT characteristic.
        return Random.nextFloat() > 0.15f
    }

    /** Clears the in-memory cache (e.g. on logout). */
    suspend fun clear() = mutex.withLock { latest.clear() }
}
