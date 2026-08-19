package com.secureguard.enterprise.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WiFi-Erkennung über BSSID-Abgleich.
 *
 * Liest die zuletzt gescannten WLAN-Netzwerke (`WifiManager.getScanResults()`)
 * und gleicht die BSSIDs mit der MAC-Adresse des Assets ab. Erfordert
 * Standort-Berechtigung und (ab API 33) NEARBY_WIFI_DEVICES bzw. Standort.
 *
 * Hinweis: Ein passives Mithören von "Probe Requests" ist auf dem öffentlichen
 * Android-API nicht möglich; diese Implementierung nutzt die verfügbaren
 * ScanResults (aktives Nachbarn-Scanning), was der realistischen Alternative
 * entspricht.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasWifiPermission()) return null
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null

        // Bei Bedarf einen neuen Scan anstoßen (Ergebnisse kommen asynchron,
        // wir nutzen die zuletzt verfügbaren Ergebnisse).
        if (!wifiManager.isWifiEnabled) return null

        val results = runCatching { wifiManager.scanResults }.getOrNull() ?: return null

        val match = results.firstOrNull { result ->
            result.BSSID.equals(asset.mac, ignoreCase = true)
        } ?: return null

        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = match.BSSID,
            rssi = match.level,
            timestamp = Date()
        )
        _detections.tryEmit(detection)
        return detection
    }

    private fun hasWifiPermission(): Boolean {
        // Ab API 33 ist NEARBY_WIFI_DEVICES nötig, sonst Standort-Berechtigung.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
