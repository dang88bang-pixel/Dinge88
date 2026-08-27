package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsource-Kanal:
 * 1) SecureGuard-Backend `/api/crowd/search`
 * 2) optional Find-My-Proxy (`FIND_MY_PROXY_URL`) als zusätzlicher Lookup
 *
 * Nur aktiv wenn [Asset.externalAllowed] (DSGVO).
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val endpointConfig: EndpointConfig
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val backendUrl: String
        get() = endpointConfig.backendBaseUrl

    suspend fun searchAsset(asset: Asset): Detection? = withContext(Dispatchers.IO) {
        if (!asset.externalAllowed) return@withContext null

        // 1) Eigenes Backend
        searchBackend(asset)?.let { return@withContext it }

        // 2) Find-My-Proxy (optional)
        searchFindMyProxy(asset)
    }

    private fun searchBackend(asset: Asset): Detection? {
        if (backendUrl.isBlank()) return null
        return try {
            val url = "$backendUrl/api/crowd/search?mac=${asset.mac}"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                if (body.isBlank()) return null
                val sightings = gson.fromJson(body, Array<CrowdSighting>::class.java)
                if (sightings.isEmpty()) return null
                val latest = sightings.first()
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.CROWD,
                    nodeId = latest.reporterId ?: "crowd-backend",
                    rssi = latest.rssi ?: -85,
                    latitude = latest.latitude,
                    longitude = latest.longitude,
                    accuracyMeters = 80f,
                    message = "Crowd-Sichtung: ${latest.reporterId ?: "anonym"}",
                    timestamp = Date()
                ).also { emit(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun searchFindMyProxy(asset: Asset): Detection? {
        val base = endpointConfig.findMyProxyUrl
        if (base.isBlank()) return null
        return try {
            val url = "$base/api/v1/locate?mac=${java.net.URLEncoder.encode(asset.mac, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val hit = gson.fromJson(body, FindMyHit::class.java) ?: return null
                if (hit.latitude == null || hit.longitude == null) return null
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.CROWD,
                    nodeId = hit.source ?: "find-my-proxy",
                    rssi = hit.rssi ?: -90,
                    latitude = hit.latitude,
                    longitude = hit.longitude,
                    accuracyMeters = hit.accuracy ?: 150f,
                    message = "Find-My-Proxy: ${hit.source ?: "proxy"}",
                    timestamp = Date()
                ).also { emit(it) }
            }
        } catch (_: Exception) {
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

data class FindMyHit(
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("source") val source: String? = null
)
