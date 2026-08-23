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
 * Urban infrastructure channel: smart-city sensors, public transport hubs,
 * charging stations and ANPR cameras operated by partner networks.
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val nodes = listOf(
        Triple("hub-hbf", 52.5255, 13.3695),
        Triple("hub-ostkreuz", 52.5040, 13.4680),
        Triple("charger-mitte", 52.5260, 13.3920),
        Triple("anpr-auerstr", 52.4980, 13.4040)
    )

    suspend fun searchAsset(asset: Asset): Detection? {
        delay(160)
        // 100 % aktive Bereitschaft (Demo-Modus)
        val node = nodes.random()
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.URBAN,
            nodeId = node.first,
            rssi = -68,
            latitude = node.second,
            longitude = node.third,
            accuracyMeters = 35f,
            timestamp = Date()
        ).also { emit(it) }
    }
}
