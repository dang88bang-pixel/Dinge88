package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optical recognition channel (camera / YOLO inference server).
 *
 * Real integration: fragt den konfigurierten Inferenz-Endpunkt
 * (`YOLO_SERVER_URL`) per HTTP-POST nach Sichtungshinweisen für das Asset.
 * Erwartete Antwort (JSON): {"found":true,"lat":..,"lng":..,"confidence":..,
 * "camera":"cam-07"}. Ohne konfigurierten Server oder ohne Treffer wird
 * `null` geliefert — keine simulierten Sichtungsmeldungen.
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val isConfigured: Boolean get() = BuildConfig.YOLO_SERVER_URL.isNotBlank()

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!isConfigured) return null
        val sighting = queryInferenceServer(asset.mac) ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = sighting.camera,
            rssi = -100,
            latitude = sighting.lat ?: asset.latitude,
            longitude = sighting.lng ?: asset.longitude,
            accuracyMeters = 12f,
            message = "Optische Erkennung (Konfidenz ${(sighting.confidence * 100).toInt()}%)",
            timestamp = Date()
        ).also { emit(it) }
    }

    private suspend fun queryInferenceServer(mac: String): Sighting? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = gson.toJson(mapOf("mac" to mac.uppercase()))
                    .toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${BuildConfig.YOLO_SERVER_URL.trimEnd('/')}/detect")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val text = response.body?.string() ?: return@runCatching null
                    val json = gson.fromJson(text, JsonObject::class.java)
                    if (json.get("found")?.takeIf { it.isJsonPrimitive }?.asBoolean != true) {
                        return@runCatching null
                    }
                    Sighting(
                        lat = json.doubleOrNull("lat"),
                        lng = json.doubleOrNull("lng"),
                        confidence = json.doubleOrNull("confidence") ?: 0.0,
                        camera = json.stringOrNull("camera") ?: "yolo-cam"
                    )
                }
            }.getOrNull()
        }

    private data class Sighting(
        val lat: Double?,
        val lng: Double?,
        val confidence: Double,
        val camera: String
    )

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asDouble

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asString
}
