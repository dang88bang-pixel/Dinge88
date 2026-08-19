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
import kotlinx.coroutines.withTimeoutOrNull
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
    @ApplicationContext private val context: Context,
    private val commandConnector: BleCommandConnector
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
     * Sendet einen Befehl an das Asset über BLE-GATT (Write).
     *
     * Verbindet sich mit der MAC-Adresse, durchsucht die Services und schreibt
     * den Befehl in die erste beschreibbare Charakteristik (WRITE oder
     * WRITE_NO_RESPONSE). Fehlertolerant: ohne Gerät/Verbindung wird `false`
     * zurückgegeben, ohne zu crashen.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        if (mac.isBlank()) return false
        if (!hasBleConnectPermission()) return false
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        if (!adapter.isEnabled) return false
        // getRemoteDevice wirft bei ungültiger MAC eine IllegalArgumentException.
        val device = runCatching { adapter.getRemoteDevice(mac) }.getOrNull() ?: return false

        return withTimeoutOrNull(GATT_TIMEOUT_MS) {
            try {
                val result = commandConnector.execute(device, command)
                result
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    /**
     * Liest die letzte Telemetrie für ein Asset (Platzhalter).
     *
     * TODO: Telemetrie (Batterie, Motor, GPS, ...) über BLE-GATT-Read abrufen.
     * Aktuell wird `null` zurückgegeben; die UI zeigt dann "Unbekannt".
     */
    suspend fun getLatestTelemetry(mac: String): Telemetry? {
        // TODO: GATT-Read für Telemetrie-Charakteristik implementieren.
        return null
    }

    private fun hasBleConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 4_000L
        private const val GATT_TIMEOUT_MS = 8_000L
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
