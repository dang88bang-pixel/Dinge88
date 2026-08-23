package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsource find-my-network channel.
 *
 * Queries the SecureGuard backend for crowd-reported sightings of an asset.
 * Only active when [Asset.externalAllowed] is true (GDPR compliance).
 * Returns null when no sightings are found or the backend is unreachable.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val backendUrl: String
        get() {
            val ws = BuildConfig.WEBSOCKET_URL
            return ws.replace("ws://", "http://")
                .replace("wss://", "https://")
                .removeSuffix("/ws")
                .trimEnd('/')
        }

    suspend fun searchAsset(asset: Asset): Detection? {
        if (!asset.externalAllowed) return null
        if (backendUrl.isBlank()) return null

        return try {
            val url = "$backendUrl/api/crowd/search?mac=${asset.mac}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body?.string()
            response.close()
            if (body.isNullOrBlank()) return null

            val sightings = gson.fromJson(body, Array<CrowdSighting>::class.java)
            if (sightings.isEmpty()) return null

            val latest = sightings.first()
            Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.CROWD,
                nodeId = latest.reporterId ?: "crowd-unknown",
                rssi = latest.rssi ?: -85,
                latitude = latest.latitude,
                longitude = latest.longitude,
                accuracyMeters = 80f,
                message = "Crowd-Sichtung: ${latest.reporterId ?: "anonym"}",
                timestamp = Date()
            ).also { emit(it) }
        } catch (e: Exception) {
            null
        }
    }
}

data class CrowdSighting(
    @SerializedName("mac") val mac: String? = null,
    @SerializedName("reporter_id") val reporterId: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("timestamp") val timestamp: String? = null
)
