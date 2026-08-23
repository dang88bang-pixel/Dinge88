package com.secureguard.enterprise.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Active + passive Bluetooth Low Energy scanning. For an explicit asset search
 * a short (2 s) filtered scan is run; a real hit for the asset's MAC is
 * preferred. If BLE is unavailable or nothing is seen, `null` is returned –
 * a simulated hit is **only** produced when the explicit demo mode is enabled
 * ([RuntimeSettings.demoMode]), so the pipeline stays demonstrable without
 * faking production data.
 */
@Singleton
class BleService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings
) : DetectionCapable() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private fun hasScanPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, needed) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun searchAsset(asset: Asset): Detection? {
        val hit = if (hasScanPermission() && bluetoothAdapter != null) {
            runHardwareScan(asset)
        } else null

        if (hit != null) return hit

        // Kein echter Treffer: nur im expliziten Demo-Modus simulieren.
        if (!runtimeSettings.demoMode) return null
        delay(150)
        val rssi = -35 - Random.nextInt(0, 45)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.BLE,
            nodeId = "ble-sim (Demo)",
            rssi = rssi,
            latitude = asset.latitude ?: 52.5200,
            longitude = asset.longitude ?: 13.4050,
            accuracyMeters = 5f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runHardwareScan(asset: Asset): Detection? {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return null
        val resultDeferred = CompletableDeferred<ScanResult?>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device?.address.equals(asset.mac, ignoreCase = true) &&
                    !resultDeferred.isCompleted
                ) {
                    resultDeferred.complete(result)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.firstOrNull {
                    it.device?.address.equals(asset.mac, ignoreCase = true)
                }?.let { if (!resultDeferred.isCompleted) resultDeferred.complete(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (!resultDeferred.isCompleted) resultDeferred.complete(null)
            }
        }

        val filter = ScanFilter.Builder()
            .setDeviceAddress(asset.mac)
            .build()

        val started = runCatching {
            scanner.startScan(listOf(filter), SETTINGS, callback)
        }.isSuccess
        if (!started) return null

        return try {
            val scanResult = withTimeoutOrNull(SCAN_DURATION_MS) { resultDeferred.await() }
            runCatching { scanner.stopScan(callback) }

            scanResult?.let {
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.BLE,
                    nodeId = "ble-${it.device?.address}",
                    rssi = it.rssi,
                    latitude = asset.latitude,
                    longitude = asset.longitude,
                    accuracyMeters = 5f,
                    timestamp = Date()
                ).also { d -> emit(d) }
            }
        } finally {
            runCatching { scanner.stopScan(callback) }
        }
    }

    companion object {
        private const val SCAN_DURATION_MS = 2_000L
        private val SETTINGS = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }
}
