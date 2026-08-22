package com.secureguard.enterprise.services

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
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

/**
 * WiFi-basierte Proximity-Erkennung: echter WLAN-Scan über
 * [WifiManager] (Trigger via `startScan()`, Auswertung der realen
 * `scanResults`). Ein Treffer liegt vor, wenn die BSSID eines Access
 * Points mit der MAC-Adresse des Assets identisch ist (z. B. bei
 * direkt verbundenen E-Scooter-Modulen).
 *
 * Die Koordinaten eines Treffers kommen von der echten GPS-Position des
 * Geräts ([SatelliteService]); ohne Standortberechtigung, ohne Scan-Ergebnis
 * oder ohne BSSID-Treffer → `null` (keine simulierten Werte).
 *
 * Hinweis: Moderne Android-Versionen drosseln `getScanResults()` (ab API 31
 * nur noch das verbundene Netzwerk ohne besondere Berechtigungen) – die
 * Funktion bleibt damit platform-limitiert, liefert aber immer nur echte
 * Daten.
 */
@Singleton
class WifiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val satelliteService: SatelliteService
) : DetectionCapable() {

    private val wifiManager: WifiManager? by lazy {
        ContextCompat.getSystemService(context, WifiManager::class.java)
    }

    @SuppressLint("MissingPermission")
    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasLocationPermission() || wifiManager == null) return null

        return withContext(Dispatchers.IO) {
            try {
                // Echten Scan anstoßen und kurz auf Ergebnisse warten.
                runCatching { wifiManager.startScan() }
                delay(SCAN_DURATION_MS)

                val results = wifiManager.scanResults.orEmpty()
                val match = results.firstOrNull {
                    it.BSSID.equals(asset.mac, ignoreCase = true)
                }
                if (match == null) return@withContext null

                val location = satelliteService.currentLocation()
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.WIFI,
                    nodeId = match.BSSID,
                    rssi = match.level,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    accuracyMeters = rssiToAccuracyMeters(match.level),
                    message = "SSID=${match.SSID ?: "?"} · ${match.frequency} MHz",
                    timestamp = Date()
                ).also { emit(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun rssiToAccuracyMeters(rssi: Int): Float = when {
        rssi > -55 -> 3f
        rssi > -67 -> 10f
        rssi > -80 -> 30f
        else -> 80f
    }

    companion object {
        private const val SCAN_DURATION_MS = 2_500L
    }
}
