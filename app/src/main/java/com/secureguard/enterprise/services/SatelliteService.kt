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
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Satellite / GNSS fallback channel. Used when all terrestrial channels fail,
 * e.g. for assets in remote areas.
 *
 * Die echte GPS-Position des Geräts wird über [FusedLocationProviderClient]
 * (Google Play Services) ermittelt – das Gerät selbst dient als Referenzpunkt.
 * Ohne Standortberechtigung oder ohne GPS-Fix wird `null` geliefert — kein
 * simulierter Schätzwert.
 */
@Singleton
class SatelliteService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun searchAsset(asset: Asset): Detection? {
        val location = currentLocation() ?: return null
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
