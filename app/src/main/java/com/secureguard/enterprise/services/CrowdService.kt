package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.JsonObject
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsource-Channel (Find-My-Netzwerk-Proxy, Apple/Google-Stil).
 *
 * Wird NUR abgefragt, wenn [Asset.externalAllowed] = true ist (DSGVO:
 * ausdrückliche Einwilligung pro Asset). Echter HTTP-Call gegen das
 * Pilot-Backend (`GET /api/crowd/locate?mac=`); das Backend aggregiert die
 * Find-My-Proxy-Instanz. Ohne Einwilligung, Backend oder Treffer → `null`.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null

        val element = BackendHttp.getJson("/api/crowd/locate", mapOf("mac" to asset.mac))
        val obj = element as? JsonObject ?: return null
        if (obj.get("found")?.takeIf { it.isBoolean }?.asBoolean != true) return null

        val latitude = obj.get("latitude")?.takeIf { it.isNumber }?.asDouble
        val longitude = obj.get("longitude")?.takeIf { it.isNumber }?.asDouble
        if (latitude == null || longitude == null) return null

        val accuracyMeters = obj.get("accuracy")?.takeIf { it.isNumber }?.asInt?.toFloat()?.coerceAtLeast(1f)
            ?: 100f // Crowd-Netzwerk-Default

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = obj.get("network")?.takeIf { !it.isJsonNull }?.asString ?: "crowd-proxy",
            rssi = 0,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestamp = Date()
        ).also { emit(it) }
    }
}
