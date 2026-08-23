package com.secureguard.enterprise.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
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

/**
 * WiFi based proximity detection.
 *
 * Real implementation: triggers a WifiManager scan and matches the asset's
 * MAC/BSSID against the hardware scan results. A Detection is only produced
 * when the BSSID was actually observed. Without location permission (and
 * NEARBY_WIFI_DEVICES on API 33+) or without a match, `null` is returned —
 * no simulated channel.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    @SuppressLint("MissingPermission")
    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasScanPermission()) return null

        val results = runScan() ?: return null
        val hit = results.firstOrNull { it.BSSID.equals(asset.mac, ignoreCase = true) }
            ?: return null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = "wifi-${hit.BSSID}",
            rssi = hit.level,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 15f,
            message = "WiFi-BSSID ${hit.BSSID} · ${hit.level} dBm",
            timestamp = Date()
        ).also { emit(it) }
    }

    /**
     * Aktuelle Scan-Ergebnisse als Access-Point-Liste (für die echte
     * Google-Geolocation-Kette — reale BSSIDs + echte Signalstärken).
     */
    @SuppressLint("MissingPermission")
    suspend fun currentAccessPoints(maxAps: Int = 8): List<AccessPointInfo> {
        if (!hasScanPermission()) return emptyList()
        val results = runScan() ?: return emptyList()
        return results
            .sortedByDescending { it.level }
            .take(maxAps)
            .map { AccessPointInfo(bssid = it.BSSID, rssi = it.level) }
    }

    /** Löst einen echten Scan aus und wartet auf die (ggf. gecachten) Ergebnisse. */
    @SuppressLint("MissingPermission")
    private suspend fun runScan(): List<ScanResult>? {
        val manager = wifiManager ?: return null
        val fresh = runCatching { manager.startScan() }.getOrDefault(false)

        // startScan() ist ab API 28 gedrosselt: Bei Throttling liefert der
        // System-Cache trotzdem die letzte echte Scan-Runde.
        repeat(if (fresh) 10 else 2) {
            val results = runCatching { manager.scanResults }.getOrNull()
            if (!results.isNullOrEmpty()) return results
            delay(200)
        }
        return runCatching { manager.scanResults }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun hasScanPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearby = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return fineLocation && nearby
    }
}

/** Realer Access-Point aus dem WiFi-Scan (BSSID + echte Signalstärke). */
data class AccessPointInfo(
    val bssid: String,
    val rssi: Int
)
