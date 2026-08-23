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
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsource find-my-network channel (Apple Find My / Google Find My Device
 * style networks). This channel is only queried when [Asset.externalAllowed] is
 * true, keeping the solution GDPR compliant and under the user's control.
 *
 * Real integration: fragt den konfigurierten Find-My-Proxy
 * (`FIND_MY_PROXY_URL`) per HTTP-POST. Es verlässt ausschließlich der
 * SHA-256-Hash der MAC das Gerät — keine Klartext-Identifikatoren.
 * Erwartete Antwort (JSON): {"found":true,"lat":..,"lng":..,"accuracy":..}.
 * Ohne konfigurierten Proxy, ohne Einwilligung oder ohne Treffer → `null`.
 */
@Singleton
class CrowdService @Inject constructor(
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

    val isConfigured: Boolean get() = BuildConfig.FIND_MY_PROXY_URL.isNotBlank()

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null
        if (!isConfigured) return null

        val idHash = sha256(asset.mac.uppercase())
        val location = queryFindMyProxy(idHash) ?: return null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.CROWD,
            nodeId = "crowd-${idHash.take(8)}",
            rssi = -100,
            latitude = location.lat,
            longitude = location.lng,
            accuracyMeters = (location.accuracy ?: 80f).coerceAtLeast(10f),
            message = "Crowd-Netzwerk-Treffer (Hash ${idHash.take(8)}…)",
            timestamp = Date()
        ).also { emit(it) }
    }

    private suspend fun queryFindMyProxy(idHash: String): Location? =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = gson.toJson(mapOf("idHash" to idHash))
                    .toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("${BuildConfig.FIND_MY_PROXY_URL.trimEnd('/')}/locate")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val text = response.body?.string() ?: return@runCatching null
                    val json = gson.fromJson(text, JsonObject::class.java)
                    if (json.get("found")?.takeIf { it.isJsonPrimitive }?.asBoolean != true) {
                        return@runCatching null
                    }
                    Location(
                        lat = json.doubleOrNull("lat") ?: return@runCatching null,
                        lng = json.doubleOrNull("lng") ?: return@runCatching null,
                        accuracy = json.doubleOrNull("accuracy")?.toFloat()
                    )
                }
            }.getOrNull()
        }

    private data class Location(
        val lat: Double,
        val lng: Double,
        val accuracy: Float?
    )

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun JsonObject.doubleOrNull(key: String): Double? =
        get(key)?.takeIf { it.isJsonPrimitive }?.takeIf { !it.isJsonNull }?.asDouble
}
