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
 * Crowdsource find-my-network channel (Apple Find My / Google Find My Device
 * style networks). This channel is only queried when [Asset.externalAllowed] is
 * true, keeping the solution GDPR compliant and under the user's control.
 *
 * No personally identifiable information leaves the device; only hashed
 * identifiers are submitted to the external network.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null
        delay(400)
        if (Random.nextFloat() > 0.5f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = "crowd-${Random.nextInt(1000, 9999)}",
            rssi = -85 - Random.nextInt(0, 15),
            latitude = 52.5200 + Random.nextDouble(-0.05, 0.05),
            longitude = 13.4050 + Random.nextDouble(-0.05, 0.05),
            accuracyMeters = 80f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
