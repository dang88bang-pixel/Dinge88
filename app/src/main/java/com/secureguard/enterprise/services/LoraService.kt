package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Generic LoRa / LoRaWAN service.
 *
 * Produktivbetrieb: Über die Einstellungen („Backend-Endpunkte") wird ein
 * echter LoRaWAN-Backend-Endpunkt konfiguriert (z. B. ein TTN/TTI-Proxy oder
 * das eigene Gateway-Fleet-Backend). [HttpLoraClient] fragt diesen per
 * `GET <url>` ab und erwartet ein JSON der Form
 *
 * `{"gateways":[{"id":"gw-1","rssi":-48,"latitude":52.52,"longitude":13.40,
 *   "seenMacs":["AA:BB:CC:DD:EE:01"]}]}`
 * (alternativ ein reines JSON-Array der Gateways).
 *
 * Befehle werden per `POST <url>/downlink` mit
 * `{"mac":"...","command":"..."}` gesendet (HTTP 2xx = zugestellt).
 *
 * Nur im expliziten Demo-Modus ([RuntimeSettings.demoMode]) liefert der
 * [DummyLoraClient] simulierte Gateways. Ohne Endpunkt und ohne Demo-Modus
 * gibt der Kanal ehrlich `null` zurück.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeSettings: RuntimeSettings,
    private val httpClient: RemoteEndpointClient
) : DetectionCapable() {

    private val loraClient: LoraClient
        get() = if (runtimeSettings.demoMode) {
            DummyLoraClient
        } else {
            HttpLoraClient(runtimeSettings.loraEndpoint, runtimeSettings.loraApiKey, httpClient)
        }

    /** Letztbekannte Gateways (real abgeholt oder – nur im Demo-Modus – simuliert). */
    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? {
        val currentGateways = refreshGateways()
        for (gw in currentGateways) {
            if (gw.seenMacs.any { it.equals(asset.mac, ignoreCase = true) }) {
                val detection = Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.LORA,
                    nodeId = gw.id,
                    rssi = gw.rssi,
                    latitude = gw.latitude,
                    longitude = gw.longitude,
                    accuracyMeters = 25f,
                    message = if (runtimeSettings.demoMode) "Demo-Modus (simuliert)" else null,
                    timestamp = Date()
                )
                emit(detection)
                return detection
            }
        }
        return null
    }

    /**
     * Sendet einen Befehl an ein LoRa-Asset:
     * 1. echter Downlink über den konfigurierten Endpunkt (HTTP 2xx),
     * 2. im Demo-Modus simulierte Zustellung (Gateway in Reichweite),
     * 3. ohne Endpunkt/Demo: `false` (nicht zustellbar).
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        val endpoint = runtimeSettings.loraEndpoint
        if (endpoint.isBlank()) {
            if (!runtimeSettings.demoMode) return false
            return DummyLoraClient.getGateways().any { gw ->
                gw.seenMacs.any { it.equals(mac, ignoreCase = true) }
            }
        }
        val body = JsonObject().apply {
            addProperty("mac", mac)
            addProperty("command", command)
        }
        return httpClient.postExpectOk(
            url = endpoint.trimEnd('/') + "/downlink",
            body = body,
            apiKey = runtimeSettings.loraApiKey
        )
    }

    suspend fun refreshGateways(): List<Gateway> {
        gateways = withContext(Dispatchers.IO) { loraClient.getGateways() }
        return gateways
    }
}

/** A LoRa gateway that recently reported sightings. */
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)

/** Contract for a pluggable LoRa / LoRaWAN backend. */
interface LoraClient {
    suspend fun getGateways(): List<Gateway>
}

/**
 * Echter LoRaWAN-Backend-Client: lädt die Gateways vom konfigurierten
 * Endpunkt (JSON). Bei fehlender URL oder Fehlern: leere Liste.
 */
internal class HttpLoraClient(
    private val endpoint: String,
    private val apiKey: String,
    private val client: RemoteEndpointClient
) : LoraClient {

    override suspend fun getGateways(): List<Gateway> {
        if (endpoint.isBlank()) return emptyList()
        val json: JsonElement = client.getJson(endpoint, apiKey) ?: return emptyList()
        val array: JsonArray = when {
            json.isJsonArray -> json.asJsonArray
            json.isJsonObject && json.asJsonObject.has("gateways") &&
                json.asJsonObject.get("gateways").isJsonArray ->
                json.asJsonObject.getAsJsonArray("gateways")
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val macs = obj.getAsJsonArray("seenMacs")?.mapNotNull { m ->
                runCatching { m.asString }.getOrNull()
            } ?: emptyList()
            Gateway(
                id = obj.get("id")?.asString ?: return@mapNotNull null,
                rssi = obj.get("rssi")?.asInt ?: -100,
                latitude = obj.get("latitude")?.asDouble
                    ?: obj.get("lat")?.asDouble ?: return@mapNotNull null,
                longitude = obj.get("longitude")?.asDouble
                    ?: obj.get("lng")?.asDouble ?: return@mapNotNull null,
                seenMacs = macs
            )
        }
    }
}

/**
 * Simulierter Client – **nur im expliziten Demo-Modus** aktiv
 * ([RuntimeSettings.demoMode]). Fabriciert Gateways um Berlin.
 */
internal object DummyLoraClient : LoraClient {

    private val pool = listOf(
        Gateway("gw-berlin-mitte", -48, 52.5200, 13.4050, listOf("AA:BB:CC:DD:EE:01")),
        Gateway("gw-kreuzberg", -62, 52.4980, 13.4040, listOf("AA:BB:CC:DD:EE:02")),
        Gateway("gw-prenzlauer-berg", -75, 52.5380, 13.4200, listOf("AA:BB:CC:DD:EE:03")),
        Gateway("gw-alexanderplatz", -55, 52.5219, 13.4132, listOf("AA:BB:CC:DD:EE:04"))
    )

    override suspend fun getGateways(): List<Gateway> {
        // Simulierter Netzwerk-Jitter (nur Demo-Modus).
        return pool.filter { Random.nextFloat() > 0.25f }
            .map { it.copy(rssi = it.rssi + Random.nextInt(-6, 6)) }
    }
}
