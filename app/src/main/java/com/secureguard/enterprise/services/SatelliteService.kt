package com.secureguard.enterprise.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Satellitenortung (GPS / GLONASS / Galileo) über die Geräte-Position.
 *
 * Liest die aktuelle GPS-Position des Geräts (FusedLocationProviderClient).
 * Diese Position dient als Detection-Referenzpunkt (z. B. "Asset in der Nähe
 * dieser Koordinate gesichtet").
 *
 * Erfordert die Berechtigung ACCESS_FINE_LOCATION.
 */
@Singleton
class SatelliteService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!hasLocationPermission()) return null

        val location = getCurrentLocation() ?: return null

        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.GPS,
            nodeId = "gps-${location.time}",
            rssi = 0,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = Date(location.time),
            isVerified = false,
            triangulationPoints = 1
        )
        _detections.tryEmit(detection)
        return detection
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getCurrentLocation(): Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        // Zuerst die letzte bekannte Position (schnell, oft ausreichend).
        val lastKnown = runCatching { awaitOrNull(client.lastLocation) }.getOrNull() ?: null
        if (lastKnown != null) return lastKnown

        // Sonst aktuelle Position mit kurzem Timeout anfordern.
        val task = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        }.getOrNull() ?: return null
        return awaitOrNull(task)
    }

    /** Wandelt eine Google-Task in eine suspend-Operation um; gibt bei Fehler null zurück. */
    private suspend fun <T> awaitOrNull(task: Task<T>): T? =
        suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener { t ->
                if (cont.isActive) {
                    if (t.isSuccessful) cont.resume(t.result) else cont.resume(null)
                }
            }
            cont.invokeOnCancellation { }
        }
}
