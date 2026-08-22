package com.secureguard.enterprise.services

import com.google.gson.JsonParser
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optionaler HTTP-Client für konfigurierbare Backend-Endpunkte
 * (Einstellungen → Backend-Endpunkte). Ohne URL greifen die Kanäle
 * auf ihre lokale Demo-Implementierung zurück.
 *
 * Erwartetes JSON: `{ "lat"|"latitude", "lng"|"longitude", "rssi", "nodeId", "accuracy", "message" }`
 */
@Singleton
class RemoteDetectionFetcher @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(baseUrl: String, asset: Asset, source: DetectionSource): Detection? =
        withContext(Dispatchers.IO) {
            val trimmed = baseUrl.trim()
            if (trimmed.isEmpty()) return@withContext null
            val url = if (trimmed.contains('?')) {
                "$trimmed&mac=${asset.mac}"
            } else {
                "${trimmed.trimEnd('/')}?mac=${asset.mac}"
            }
            runCatching {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) return@use null
                    val json = JsonParser.parseString(body).asJsonObject
                    Detection(
                        assetMac = asset.mac,
                        sourceType = source,
                        nodeId = json.get("nodeId")?.takeUnless { it.isJsonNull }?.asString ?: "remote",
                        rssi = json.get("rssi")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                        latitude = json.get("latitude")?.takeUnless { it.isJsonNull }?.asDouble
                            ?: json.get("lat")?.takeUnless { it.isJsonNull }?.asDouble,
                        longitude = json.get("longitude")?.takeUnless { it.isJsonNull }?.asDouble
                            ?: json.get("lng")?.takeUnless { it.isJsonNull }?.asDouble,
                        accuracyMeters = json.get("accuracy")?.takeUnless { it.isJsonNull }?.asFloat ?: 50f,
                        message = json.get("message")?.takeUnless { it.isJsonNull }?.asString,
                        timestamp = Date()
                    )
                }
            }.getOrNull()
        }
}
