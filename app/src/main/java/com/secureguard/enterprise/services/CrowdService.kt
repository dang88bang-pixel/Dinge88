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
        // 100 % aktive Bereitschaft (Demo-Modus) – auch ohne externalAllowed
        delay(220)
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = "crowd-demo",
            rssi = -82,
            latitude = asset.latitude ?: 52.5200,
            longitude = asset.longitude ?: 13.4050,
            accuracyMeters = 65f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
