package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonElement
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
 * Urbaner Infrastruktur-Kanal: Smart-City-Sensoren, Verkehrsknoten,
 * Ladesäulen-Netze und ANPR-Kameras von Partnernetzen.
 *
 * Produktivbetrieb: Der Infrastruktur-Endpunkt (Open-Data-/Partner-API) wird
 * in den Einstellungen konfiguriert. Abfrage: `GET <url>?mac=<MAC>`; erwartete
 * Antwort (Objekt oder Array):
 *
 * `{"found":true,"node":"hub-hbf","rssi":-70,"latitude":52.5255,"longitude":13.3695}`
 *
 * `found:false` bzw. leeres Array = nichts gesehen. Nur im expliziten
 * Demo-Modus ([RuntimeSettings.demoMode]) bleiben die festen Beispiel-Knoten
 * simuliert.
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings,
    private val httpClient: RemoteEndpointClient
) : DetectionCapable() {

    /** Feste Beispiel-Knoten – ausschließlich für den Demo-Modus. */
    private val demoNodes = listOf(
        Triple("hub-hbf", 52.5255, 13.3695),
        Triple("hub-ostkreuz", 52.5040, 13.4680),
        Triple("charger-mitte", 52.5260, 13.3920),
        Triple("anpr-auerstr", 52.4980, 13.4040)
    )

    suspend fun searchAsset(asset: Asset): Detection? {
        val endpoint = runtimeSettings.urbanEndpoint
        if (endpoint.isBlank()) {
            return if (runtimeSettings.demoMode) simulateDemoSighting(asset) else null
        }

        val url = StringBuilder(endpoint).apply {
            if (!endpoint.contains('?')) append('?') else append('&')
            append("mac=").append(android.net.Uri.encode(asset.mac))
        }.toString()
        val response: JsonElement = httpClient.getJson(url) ?: return null

        val obj = when {
            response.isJsonObject -> response.asJsonObject
            response.isJsonArray && response.asJsonArray.size() > 0 ->
                response.asJsonArray[0].asJsonObject
            else -> return null
        }
        if (!obj.get("found")?.asBoolean ?: false) return null

        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.URBAN,
            nodeId = obj.get("node")?.asString
                ?: obj.get("id")?.asString ?: "urban",
            rssi = obj.get("rssi")?.asInt ?: -70,
            latitude = obj.get("latitude")?.asDouble ?: obj.get("lat")?.asDouble,
            longitude = obj.get("longitude")?.asDouble ?: obj.get("lng")?.asDouble,
            accuracyMeters = obj.get("accuracyMeters")?.asFloat ?: 40f,
            timestamp = Date()
        )
        emit(detection)
        return detection
    }

    /** Simulation – nur aktiv, wenn der Demo-Modus explizit eingeschaltet ist. */
    private suspend fun simulateDemoSighting(asset: Asset): Detection? {
        delay(250)
        if (Random.nextFloat() > 0.45f) return null
        val node = demoNodes.random()
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.URBAN,
            nodeId = "${node.first} (Demo)",
            rssi = -70 - Random.nextInt(0, 20),
            latitude = node.second + Random.nextDouble(-0.002, 0.002),
            longitude = node.third + Random.nextDouble(-0.002, 0.002),
            accuracyMeters = 40f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }
}
