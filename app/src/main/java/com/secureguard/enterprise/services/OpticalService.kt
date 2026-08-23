package com.secureguard.enterprise.services

import android.content.Context
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
 * Optical recognition (camera / QR / VIN plate scan).
 *
 * A real implementation would run a detector model on camera frames; this
 * placeholder simulates occasional sightings and is wired into the agent the
 * same way every other channel is.
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        delay(180)
        // 100 % aktive Bereitschaft (Demo-Modus)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "cam-demo",
            rssi = -75,
            latitude = asset.latitude ?: 52.5200,
            longitude = asset.longitude ?: 13.4050,
            accuracyMeters = 15f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
