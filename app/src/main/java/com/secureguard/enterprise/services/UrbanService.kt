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
 * Urban infrastructure channel: smart-city sensors, public transport hubs,
 * charging stations and ANPR cameras operated by partner networks.
 *
 * Real integration: fragt den konfigurierten Partner-/Smart-City-Endpunkt
 * (`URBAN_SIGHTINGS_URL`) per HTTP-POST nach Sichtungshinweisen. Erwartete
 * Antwort (JSON): {"found":true,"node":"hub-hbf","lat":..,"lng":..,
 * "rssi":..}. Ohne konfigurierten Endpunkt oder ohne Treffer wird `null`
 * geliefert — keine erfundenen Infrastruktur-Knoten.
 */
@Singleton
class UrbanService @Inject constructor(
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

    val isConfigured: Boolean get() = BuildConfig.URBAN_SIGHTINGS_URL.isNotBlank()

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!isConfigured) return null
        val sighting = queryUrbanEndpoint(asset.mac) ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.URBAN,
            nodeId = sighting.node,
            rssi = sighting.rssi,
            latitude = sighting.lat ?: asset.latitude,
            longitude = sighting.lng ?: asset.longitude,
            accuracyMeters = 40f,
            message = "Urban-Knoten ${sighting.node}",
            timestamp = Date()
        ).also { emit(it) }
    }

    private suspend fun queryUrbanEndpoint(mac: String): Sighting? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = gson.toJson(mapOf("mac" to mac.uppercase()))
                    .toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${BuildConfig.URBAN_SIGHTINGS_URL.trimEnd('/')}/sightings")
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
                        node = json.stringOrNull("node") ?: "urban-node",
                        rssi = json.intOrNull("rssi") ?: -80,
                        lat = json.doubleOrNull("lat"),
                        lng = json.doubleOrNull("lng")
                    )
                }
            }.getOrNull()
        }

    private data class Sighting(
        val node: String,
        val rssi: Int,
        val lat: Double?,
        val lng: Double?
    )

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asDouble

    private fun JsonObject.intOrNull(key: String): Int? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asInt

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asString
}
