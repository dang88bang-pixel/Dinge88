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
 * Liest Telemetrie (Batterie, Kraftstoff, Motorzustand, Position, ...) vom
 * Asset über **echtes BLE/GATT** ([BleCommandConnector], Service-/Characteristic-
 * UUIDs wie in der ESP32-Firmware) und sendet Befehle an das Asset.
 *
 * - Verbindungsdaten werden aus der echten GATT-Antwort gelesen und gecacht.
 * - Befehle werden per Write-with-Response zugestellt; erfolgreich nur bei
 *   Bestätigung durch das Gerät.
 * - Ohne erreichbares Gerät: `null` bzw. `false` – **kein** Fake. Nur im
 *   expliziten Demo-Modus ([RuntimeSettings.demoMode]) wird ein virtuelles
 *   Gerät simuliert (für Demos/Pilotbetrieb ohne Hardware).
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleCommandConnector: BleCommandConnector,
    private val runtimeSettings: RuntimeSettings
) : DetectionCapable() {

    private val latest = mutableMapOf<String, Telemetry>()
    private val mutex = Mutex()

    suspend fun searchAsset(asset: Asset): Detection? {
        val telemetry = fetchTelemetry(asset) ?: return null
        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.TELEMETRY,
            nodeId = "telemetry-gatt",
            rssi = -40 - Random.nextInt(0, 30),
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            accuracyMeters = 8f,
            message = telemetry.batteryPercent?.let { "Batterie: $it%" },
            timestamp = telemetry.timestamp
        )
        emit(detection)
        return detection
    }

    suspend fun getLatestTelemetry(mac: String): Telemetry? = mutex.withLock {
        latest[mac.uppercase()]
    }

    /**
     * Echtes GATT-Read beim Asset. Liefert `null`, wenn das Gerät nicht
     * erreichbar ist (außer im Demo-Modus).
     */
    suspend fun fetchTelemetry(asset: Asset): Telemetry? {
        if (runtimeSettings.demoMode) {
            return simulateDemoTelemetry(asset)
        }
        val telemetry = bleCommandConnector.readTelemetry(asset.mac) ?: return null
        mutex.withLock { latest[asset.mac.uppercase()] = telemetry }
        return telemetry
    }

    /** Sends a command string to the asset. Returns whether delivery succeeded. */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return dispatchCommand(mac, command)
    }

    protected open suspend fun dispatchCommand(mac: String, command: String): Boolean {
        if (runtimeSettings.demoMode) {
            delay(120)
            return Random.nextFloat() > 0.15f
        }
        // Echter GATT-Write (Write-with-Response): Erfolg nur bei Bestätigung.
        return bleCommandConnector.writeCommand(mac, command)
    }

    /** Clears the in-memory cache (e.g. on logout). */
    suspend fun clear() = mutex.withLock { latest.clear() }

    /** Simulation – nur aktiv, wenn der Demo-Modus explizit eingeschaltet ist. */
    private suspend fun simulateDemoTelemetry(asset: Asset): Telemetry {
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
}
