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
 * style networks). Der Kanal wird nur für Assets mit
 * [com.secureguard.enterprise.data.model.Asset.externalAllowed] = true
 * genutzt (DSGVO: Kontrolle beim Nutzer; nur gehashte Identifikatoren).
 *
 * Produktivbetrieb: Der eigene/autorisierte Find-My-Proxy-Endpunkt wird in
 * den Einstellungen konfiguriert. Abfrage: `GET <url>?mac=<MAC>`; erwartete
 * Antwort:
 *
 * `{"found":true,"node":"crowd-42","rssi":-85,"latitude":52.52,"longitude":13.41}`
 *
 * `found:false` oder Fehler = nicht gefunden. Nur im expliziten Demo-Modus
 * ([RuntimeSettings.demoMode]) bleibt die alte Simulation aktiv.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings,
    private val httpClient: RemoteEndpointClient
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null

        val endpoint = runtimeSettings.crowdEndpoint
        if (endpoint.isBlank()) {
            return if (runtimeSettings.demoMode) simulateDemoSighting(asset) else null
        }

        val url = StringBuilder(endpoint).apply {
            if (!endpoint.contains('?')) append('?') else append('&')
            append("mac=").append(android.net.Uri.encode(asset.mac))
        }.toString()
        val response = httpClient.getJson(url) ?: return null
        val obj = when {
            response.isJsonObject -> response.asJsonObject
            else -> return null
        }
        if (obj.get("found")?.asBoolean != true) return null

        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = obj.get("node")?.asString ?: "crowd",
            rssi = obj.get("rssi")?.asInt ?: -85,
            latitude = obj.get("latitude")?.asDouble,
            longitude = obj.get("longitude")?.asDouble,
            accuracyMeters = obj.get("accuracyMeters")?.asFloat ?: 80f,
            timestamp = Date()
        )
        emit(detection)
        return detection
    }

    /** Simulation – nur aktiv, wenn der Demo-Modus explizit eingeschaltet ist. */
    private suspend fun simulateDemoSighting(asset: Asset): Detection? {
        delay(400)
        if (Random.nextFloat() > 0.5f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = "crowd-${Random.nextInt(1000, 9999)} (Demo)",
            rssi = -85 - Random.nextInt(0, 15),
            latitude = 52.5200 + Random.nextDouble(-0.05, 0.05),
            longitude = 13.4050 + Random.nextDouble(-0.05, 0.05),
            accuracyMeters = 80f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }
}
