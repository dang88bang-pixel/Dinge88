package com.secureguard.enterprise.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Satellite / GNSS channel. Liefert die **echte** GPS-Position des Geräts über
 * [FusedLocationProviderClient] (Google Play Services) als Referenzpunkt
 * (z. B. „Asset zuletzt in meiner Nähe gesehen").
 *
 * Ohne Standortberechtigung oder GPS-Fix gibt der Kanal ehrlich `null`
 * zurück; eine simulierte Schätzposition liefert er ausschließlich im
 * expliziten Demo-Modus ([RuntimeSettings.demoMode]).
 */
@Singleton
class SatelliteService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings
) : DetectionCapable() {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun searchAsset(asset: Asset): Detection? {
        val location = currentLocation()
        if (location != null) {
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.SATELLITE,
                nodeId = "gps-fix",
                rssi = -100,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy.coerceAtLeast(1f),
                message = "GPS-Position (${location.provider ?: "gnss"})",
                timestamp = Date()
            ).also { emit(it) }
        }

        // Kein Fix verfügbar: nur im Demo-Modus eine grobe Schätzung liefern,
        // ansonsten ehrlich "nicht gefunden" zurückmelden.
        if (!runtimeSettings.demoMode) return null
        delay(500)
        if (Random.nextFloat() > 0.4f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.SATELLITE,
            nodeId = "sat-fix (Demo)",
            rssi = -100,
            latitude = asset.latitude ?: (52.5200 + Random.nextDouble(-0.08, 0.08)),
            longitude = asset.longitude ?: (13.4050 + Random.nextDouble(-0.08, 0.08)),
            accuracyMeters = 150f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }

    /** Echte GPS-Position über Google Play Services (falls berechtigt). */
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        return withContext(Dispatchers.IO) {
            try {
                Tasks.await(fusedLocationClient.lastLocation, 3, TimeUnit.SECONDS)
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
}
