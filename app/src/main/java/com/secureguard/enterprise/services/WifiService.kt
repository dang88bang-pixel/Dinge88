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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * WiFi-basierte Nähe-Erkennung.
 *
 * Echte Implementierung: Ein Scan wird angestoßen und die Ergebnisse von
 * [WifiManager.getScanResults] werden per BSSID mit der Asset-MAC abgeglichen.
 * Ein Treffer liefert die **echte** RSSI-Messung des Access Points. Ohne
 * Treffer (oder ohne Berechtigung) gibt der Kanal ehrlich `null` zurück –
 * Simulation nur im expliziten Demo-Modus ([RuntimeSettings.demoMode]).
 *
 * Hinweis: Android drosselt Hintergrund-Scans (4 Scans / 2 h ab API 28);
 * `startScan()` liefert dann trotzdem die zuletzt gecachten Ergebnisse.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    suspend fun searchAsset(asset: Asset): Detection? {
        val results = if (hasLocationPermission()) {
            withContext(Dispatchers.IO) {
                runCatching { wifiManager?.startScan() }
                // Kurzer Puffer, damit (gedrosselte) Ergebnisse vorliegen.
                delay(180)
                runCatching { wifiManager?.scanResults }.getOrNull()
            }
        } else null

        val hit = results?.firstOrNull {
            it.bssid.equals(asset.mac, ignoreCase = true)
        }

        if (hit != null) {
            val detection = Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.WIFI,
                nodeId = "wifi-ap ${hit.bssid}",
                rssi = hit.level,
                latitude = asset.latitude,
                longitude = asset.longitude,
                accuracyMeters = 15f,
                message = "SSID: ${hit.ssid.ifBlank { "unbekannt" }}",
                timestamp = Date()
            )
            emit(detection)
            return detection
        }

        // Kein realer Treffer: nur im Demo-Modus simulieren.
        if (!runtimeSettings.demoMode) return null
        delay(180)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.WIFI,
            nodeId = "wifi-ap (Demo)",
            rssi = -50 - Random.nextInt(0, 30),
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 15f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
}
