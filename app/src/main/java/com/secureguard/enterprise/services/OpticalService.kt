package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonObject
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
 * Optische Erkennung (Kamera-Netzwerke, ANPR, YOLO-Inferenz-Server).
 *
 * Produktivbetrieb: Der Inferenz-Endpunkt (z. B. YOLO-Server des Betreibers)
 * wird in den Einstellungen konfiguriert. Pro Suche wird
 * `POST <url>` mit `{"mac":"...","latitude":...,"longitude":...}` gesendet;
 * erwartete Antwort:
 *
 * `{"found":true,"node":"cam-3","rssi":-80,"latitude":52.52,"longitude":13.41}`
 *
 * `found:false` oder ein Fehler bedeutet: nichts gesehen – **kein** Fake.
 * Nur im expliziten Demo-Modus ([RuntimeSettings.demoMode]) wird der alte
 * Zufalls-Kanal simuliert.
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings,
    private val httpClient: RemoteEndpointClient
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        val endpoint = runtimeSettings.opticalEndpoint
        if (endpoint.isBlank()) {
            return if (runtimeSettings.demoMode) simulateDemoSighting(asset) else null
        }

        val body = JsonObject().apply {
            addProperty("mac", asset.mac)
            asset.latitude?.let { addProperty("latitude", it) }
            asset.longitude?.let { addProperty("longitude", it) }
        }
        val response = httpClient.postJson(endpoint, body) ?: return null
        if (response.get("found")?.asBoolean != true) return null

        val detection = Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = response.get("node")?.asString ?: "optical",
            rssi = response.get("rssi")?.asInt ?: -80,
            latitude = response.get("latitude")?.asDouble,
            longitude = response.get("longitude")?.asDouble,
            accuracyMeters = response.get("accuracyMeters")?.asFloat ?: 12f,
            timestamp = Date()
        )
        emit(detection)
        return detection
    }

    /** Simulation – nur aktiv, wenn der Demo-Modus explizit eingeschaltet ist. */
    private suspend fun simulateDemoSighting(asset: Asset): Detection? {
        delay(300)
        if (Random.nextFloat() > 0.55f) return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "cam-${Random.nextInt(1, 16)} (Demo)",
            rssi = -80 - Random.nextInt(0, 15),
            latitude = 52.5200 + Random.nextDouble(-0.02, 0.02),
            longitude = 13.4050 + Random.nextDouble(-0.02, 0.02),
            accuracyMeters = 12f,
            message = "Demo-Modus (simuliert)",
            timestamp = Date()
        ).also { emit(it) }
    }
}
