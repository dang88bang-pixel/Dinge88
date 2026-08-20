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
 * Satellite / GNSS fallback channel. Used when all terrestrial channels fail,
 * e.g. for assets in remote areas. The integration point is left generic so
 * that any satellite modem / emergency messenger provider can be plugged in.
 */
@Singleton
class SatelliteService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        delay(500)
        // Coarse, occasional fixes — satellite is the last-resort channel.
        if (Random.nextFloat() > 0.4f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.SATELLITE,
            nodeId = "sat-fix",
            rssi = -100,
            latitude = asset.latitude ?: (52.5200 + Random.nextDouble(-0.08, 0.08)),
            longitude = asset.longitude ?: (13.4050 + Random.nextDouble(-0.08, 0.08)),
            accuracyMeters = 150f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
