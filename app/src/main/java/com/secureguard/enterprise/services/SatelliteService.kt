package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Satellitenortung (GPS / GLONASS / Galileo).
 *
 * Platzhalter: Koordinaten aus der Fahrzeug-Telemetrie oder aus einem
 * satellitenbasierten (NTN / Direct-to-Cell) Kanal.
 */
@Singleton
class SatelliteService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        // TODO: GPS-/NTN-Telemetrie für das Asset abrufen.
        return null
    }
}
