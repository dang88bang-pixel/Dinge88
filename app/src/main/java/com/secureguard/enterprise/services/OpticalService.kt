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
 * Optische Erkennung (Webcam/YOLO-Server, Kennzeichen-/VIN-Anpr).
 *
 * Echter HTTP-Call gegen das Pilot-Backend (`GET /api/optical/detections?mac=`);
 * das Backend stellt die Treffer des YOLO-Servers bereit. Die neueste
 * echte Erkennung wird verwendet. Ohne Backend/Daten → `null`
 * (keine simulierten Sichte).
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        val element = BackendHttp.getJson("/api/optical/detections", mapOf("mac" to asset.mac))
        val array: JsonArray? = when {
            element is JsonArray -> element
            element is JsonObject -> element.getAsJsonArray("detections")
            else -> null
        }
        val latest = array?.firstOrNull()?.asJsonObject ?: return null

        val nodeId = latest.get("node_id")?.asString ?: "optical"
        val latitude = latest.get("latitude")?.takeIf { it.isNumber }?.asDouble
        val longitude = latest.get("longitude")?.takeIf { it.isNumber }?.asDouble
        if (latitude == null || longitude == null) return null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = nodeId,
            rssi = 0,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = 50f, // Kanal-Konstante für optische Treffer
            timestamp = Date()
        ).also { emit(it) }
    }
}
