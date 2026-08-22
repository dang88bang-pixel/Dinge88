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
 * Urban-Infrastruktur-Channel: Smart-City-Sensoren, Ladeinfrastruktur,
 * Packstationen und ANPR-Kameras.
 *
 * Echter HTTP-Call gegen das Pilot-Backend
 * (`GET /api/urban/detections?mac=`); das Backend aggregiert die realen
 * urbanen Datenquellen (WiGle.net, Open Charge Map, DHL-Packstationen,
 * CKAN-Open-Data – siehe ApiServiceManager). Beste reale Sichtung
 * (höchster RSSI) wird verwendet. Ohne Backend/Daten → `null`.
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        val element = BackendHttp.getJson("/api/urban/detections", mapOf("mac" to asset.mac))
        val array: JsonArray? = when {
            element is JsonArray -> element
            element is JsonObject -> element.getAsJsonArray("detections")
            else -> null
        }
        val sightings = array?.mapNotNull { item ->
            val obj = item.asJsonObject ?: return@mapNotNull null
            UrbanSighting(
                nodeId = obj.get("node_id")?.asString ?: "urban",
                rssi = obj.get("rssi")?.takeIf { it.isNumber }?.asInt ?: 0,
                latitude = obj.get("latitude")?.takeIf { it.isNumber }?.asDouble,
                longitude = obj.get("longitude")?.takeIf { it.isNumber }?.asDouble
            )
        }?.filter { it.latitude != null && it.longitude != null }
            ?: return null

        val best = sightings.maxByOrNull { it.rssi } ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.URBAN,
            nodeId = best.nodeId,
            rssi = best.rssi,
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = rssiToAccuracyMeters(best.rssi),
            timestamp = Date()
        ).also { emit(it) }
    }

    private fun rssiToAccuracyMeters(rssi: Int): Float = when {
        rssi > -60 -> 20f
        rssi > -75 -> 50f
        rssi > -90 -> 120f
        else -> 300f
    }

    private data class UrbanSighting(
        val nodeId: String,
        val rssi: Int,
        val latitude: Double?,
        val longitude: Double?
    )
}
