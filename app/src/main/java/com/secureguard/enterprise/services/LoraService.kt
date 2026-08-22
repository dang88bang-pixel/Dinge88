package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LoRa / LoRaWAN-Kanal: fragt echte Sighting-Daten des Pilot-Backends ab
 * (das Backend aggregiert die LoRaWAN-Gateways – ESP32 aus `firmware/`,
 * TTN/Helium-Anbindung) und sendet Befehle per echten HTTP-Call, den das
 * Backend als MQTT-Publish an das Gateway-Topic weiterleitet.
 *
 * Ohne erreichbares Backend bzw. ohne Daten → `null`/`false`.
 * Es werden keine Gateways oder RSSI-Werte simuliert.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    /** Zuletzt gemeldete Gateways (echte Daten aus dem Backend). */
    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    /**
     * Echte Suche: `GET /api/lora/sightings?mac=<MAC>` gegen das Backend.
     * Bester Treffer (höchster RSSI) wird als [Detection] emittiert.
     */
    suspend fun searchAsset(asset: Asset): Detection? {
        val element = BackendHttp.getJson("/api/lora/sightings", mapOf("mac" to asset.mac))
        val sightings = parseSightings(element)
        gateways = sightings.map { s ->
            Gateway(
                id = s.nodeId,
                rssi = s.rssi,
                latitude = s.latitude,
                longitude = s.longitude
            )
        }
        val best = sightings.maxByOrNull { it.rssi } ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.LORA,
            nodeId = best.nodeId,
            rssi = best.rssi,
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = rssiToAccuracyMeters(best.rssi),
            timestamp = Date()
        ).also { emit(it) }
    }

    /**
     * Sendet einen Befehl an ein via LoRa erreichbares Asset:
     * `POST /api/lora/command` (echtes MQTT-Publish im Backend).
     */
    suspend fun sendCommand(mac: String, command: String): Boolean =
        BackendHttp.postJson(
            "/api/lora/command",
            JsonObject().apply {
                addProperty("mac", mac)
                addProperty("command", command)
            }
        )

    suspend fun refreshGateways(): List<Gateway> {
        val element = BackendHttp.getJson("/api/lora/sightings", emptyMap())
        gateways = parseSightings(element).map {
            Gateway(id = it.nodeId, rssi = it.rssi, latitude = it.latitude, longitude = it.longitude)
        }
        return gateways
    }

    private fun parseSightings(element: JsonElement?): List<Sighting> {
        val array: JsonArray? = when {
            element is JsonArray -> element
            element is JsonObject -> element.getAsJsonArray("sightings")
            else -> null
        } ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item.asJsonObject ?: return@mapNotNull null
            Sighting(
                nodeId = obj.get("node_id")?.asString ?: "lora-gateway",
                rssi = obj.get("rssi")?.takeIf { it.isNumber }?.asInt ?: 0,
                latitude = obj.get("latitude")?.takeIf { it.isNumber }?.asDouble,
                longitude = obj.get("longitude")?.takeIf { it.isNumber }?.asDouble
            )
        }
    }

    private fun rssiToAccuracyMeters(rssi: Int): Float = when {
        rssi > -60 -> 15f
        rssi > -75 -> 40f
        rssi > -90 -> 100f
        else -> 250f
    }
}

/** Echter Gateway-Treffer aus dem Backend. */
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?,
    val seenMacs: List<String> = emptyList()
)

/** Interne Sichtungs-Zeile (Parsing). */
private data class Sighting(
    val nodeId: String,
    val rssi: Int,
    val latitude: Double?,
    val longitude: Double?
)
