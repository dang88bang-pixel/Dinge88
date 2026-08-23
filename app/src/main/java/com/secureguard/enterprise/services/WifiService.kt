package com.secureguard.enterprise.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi based proximity detection.
 *
 * Performs an active WiFi scan and checks whether any visible access point's
 * BSSID matches the asset's MAC address. Returns a real Detection with the
 * measured RSSI when a match is found, or null when the asset is not seen.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    @Suppress("DEPRECATION")
    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasLocationPermission()) return null
        val wm = wifiManager ?: return null

        // Check existing scan results first (passive, no new scan needed)
        val existingHit = runCatching {
            wm.scanResults.firstOrNull {
                it.BSSID.equals(asset.mac, ignoreCase = true)
            }
        }.getOrNull()

        if (existingHit != null) {
            return buildDetection(asset, existingHit.BSSID, existingHit.level)
        }

        // Start an active scan and wait for results
        val scanComplete = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                scanComplete.complete(Unit)
            }
        }

        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        runCatching { context.registerReceiver(receiver, filter) }

        val started = runCatching { wm.startScan() }.getOrDefault(false)
        if (!started) {
            runCatching { context.unregisterReceiver(receiver) }
            return null
        }

        return try {
            withTimeoutOrNull(SCAN_TIMEOUT_MS) { scanComplete.await() }
            val hit = runCatching {
                wm.scanResults.firstOrNull {
                    it.BSSID.equals(asset.mac, ignoreCase = true)
                }
            }.getOrNull()
            hit?.let { buildDetection(asset, it.BSSID, it.level) }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun buildDetection(asset: Asset, bssid: String, rssi: Int): Detection {
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = "wifi-$bssid",
            rssi = rssi,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 20f,
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val SCAN_TIMEOUT_MS = 5_000L
    }
}
