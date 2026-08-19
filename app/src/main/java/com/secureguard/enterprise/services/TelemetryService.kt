package com.secureguard.enterprise.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telemetrie-Dienst für BLE-Scans (aktives Scannen nach Geräten) sowie
 * passives Mithören von WiFi-Probe-Requests.
 */
@Singleton
class TelemetryService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    private val scanning = AtomicBoolean(false)

    /**
     * Sucht per BLE nach dem Asset. Bricht bei der ersten Übereinstimmung ab.
     * Erfordert die Berechtigung BLUETOOTH_SCAN (bzw. BLUETOOTH auf API < 31).
     */
    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasBlePermissions()) return null
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        val scanner: BluetoothLeScanner = adapter.bluetoothLeScanner ?: return null
        if (!scanning.compareAndSet(false, true)) return null

        try {
            var found: Detection? = null
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    if (device.address.equals(asset.mac, ignoreCase = true)) {
                        val detection = Detection(
                            assetMac = asset.mac,
                            sourceType = DetectionSource.BLE,
                            nodeId = device.address,
                            rssi = result.rssi,
                            timestamp = Date()
                        )
                        _detections.tryEmit(detection)
                        if (found == null) found = detection
                    }
                }
            }
            scanner.startScan(callback)
            // Kurzer Scan-Fenster, danach stoppen.
            runCatching {
                Thread.sleep(SCAN_TIMEOUT_MS)
            }
            scanner.stopScan(callback)
            return found
        } finally {
            scanning.set(false)
        }
    }

    private fun hasBlePermissions(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sendet einen Befehl an das Asset (Platzhalter).
     *
     * TODO: Echte Umsetzung z. B. über BLE-GATT-Write, LoRa-Backend oder
     * Fernsteuerungs-Server. Aktuell wird immer `false` zurückgegeben.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        // TODO: Befehl an das Gerät senden.
        return false
    }

    /**
     * Liest die letzte Telemetrie für ein Asset (Platzhalter).
     */
    suspend fun getLatestTelemetry(mac: String): Telemetry? {
        // TODO: Telemetrie (Batterie, Motor, GPS, ...) abrufen.
        return null
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 4_000L
    }
}

/**
 * Telemetriedaten eines Assets.
 */
data class Telemetry(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val battery: Int? = null,
    val fuel: Int? = null,
    val engineOk: Boolean = true,
    val distanceKm: Double? = null,
    val operatingHours: Double? = null
)
