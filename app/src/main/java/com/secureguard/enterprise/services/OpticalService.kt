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
        delay(300)
        // Optical matches are intentionally less reliable than BLE/LoRa.
        if (Random.nextFloat() > 0.55f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "cam-${Random.nextInt(1, 16)}",
            rssi = -80 - Random.nextInt(0, 15),
            latitude = 52.5200 + Random.nextDouble(-0.02, 0.02),
            longitude = 13.4050 + Random.nextDouble(-0.02, 0.02),
            accuracyMeters = 12f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
