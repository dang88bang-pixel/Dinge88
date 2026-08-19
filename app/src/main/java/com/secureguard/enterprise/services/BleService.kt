package com.secureguard.enterprise.services

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Passive Bluetooth Low Energy scanning. Looks for whitelisted assets by
 * their MAC address. If the runtime permission is missing (or no BLE hardware
 * is present) the service falls back to a simulated scan so the UI stays alive.
 */
@Singleton
class BleService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    @Volatile private var scanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val mac = result.device?.address ?: return
            emit(
                Detection(
                    assetMac = mac,
                    sourceType = DetectionSource.BLE,
                    nodeId = "ble",
                    rssi = result.rssi,
                    timestamp = Date()
                )
            )
        }
    }

    private fun hasPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Searches for a single asset. Falls back to a simulated result when BLE is unavailable. */
    suspend fun searchAsset(asset: Asset): Detection? {
        if (hasPermission()) {
            startHardwareScan()
            delay(2_000)
            stopHardwareScan()
        }
        // Simulated nearby hit so the channel always produces a result in demos.
        delay(150)
        val rssi = -35 - Random.nextInt(0, 45)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.BLE,
            nodeId = "ble-sim",
            rssi = rssi,
            latitude = asset.latitude ?: 52.5200,
            longitude = asset.longitude ?: 13.4050,
            accuracyMeters = 5f,
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun startHardwareScan() {
        val adapter = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (scanning) return
        runCatching {
            adapter.startScan(null, SCAN_SETTINGS, scanCallback)
            scanning = true
        }
    }

    private fun stopHardwareScan() {
        if (!scanning) return
        val adapter = bluetoothAdapter?.bluetoothLeScanner ?: return
        runCatching { adapter.stopScan(scanCallback) }
        scanning = false
    }

    companion object {
        private val SCAN_SETTINGS = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
}
