package com.secureguard.enterprise.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
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
 * WiFi based proximity detection. On modern Android, passive scan results from
 * [WifiManager.getScanResults] are heavily throttled and require location
 * permission, so this class mainly provides a simulated channel used by the
 * agent. It keeps the same [DetectionCapable] contract as the other services.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    suspend fun searchAsset(asset: Asset): Detection? {
        if (hasLocationPermission()) {
            val hit = runCatching {
                wifiManager?.scanResults?.firstOrNull { result ->
                    result.BSSID.equals(asset.mac, ignoreCase = true)
                }
            }.getOrNull()
            if (hit != null) {
                return Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.WIFI,
                    nodeId = hit.SSID.ifBlank { "wifi-ap" },
                    rssi = hit.level,
                    latitude = asset.latitude,
                    longitude = asset.longitude,
                    accuracyMeters = 15f,
                    timestamp = Date()
                ).also { emit(it) }
            }
        }
        delay(180)
        val rssi = -50 - Random.nextInt(0, 30)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = "wifi-ap",
            rssi = rssi,
            latitude = asset.latitude ?: 52.5210,
            longitude = asset.longitude ?: 13.4100,
            accuracyMeters = 15f,
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
