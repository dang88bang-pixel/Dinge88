package com.secureguard.enterprise.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.services.apis.HeliumNetworkApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LoRa / LoRaWAN-Kanal:
 * 1) lokales LoRa-Gateway (`LORA_GATEWAY_URL`) – Sightings per HTTP
 * 2) Helium Network API (Fallback, braucht Lat/Lon)
 * 3) Befehle weiterhin per MQTT an ESP32-Gateway
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService,
    private val endpointConfig: EndpointConfig
) : DetectionCapable() {

    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val heliumApi: HeliumNetworkApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl("https://api.helium.io/")
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HeliumNetworkApi::class.java)
    }

    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? = withContext(Dispatchers.IO) {
        // 1) Eigenes LoRa-Gateway
        searchLocalGateway(asset)?.let { return@withContext it }

        // 2) Helium
        searchHelium(asset)
    }

    private fun searchLocalGateway(asset: Asset): Detection? {
        val base = endpointConfig.loraGatewayUrl
        if (base.isBlank()) return null
        return try {
            val mac = java.net.URLEncoder.encode(asset.mac, "UTF-8")
            val url = "$base/api/v1/sightings?mac=$mac"
            val request = Request.Builder().url(url).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val hits = gson.fromJson(body, Array<LoraGatewaySighting>::class.java)
                    ?: return null
                if (hits.isEmpty()) return null
                val best = hits.maxByOrNull { it.rssi ?: -999 } ?: return null
                gateways = hits.map {
                    Gateway(
                        id = it.gatewayId ?: "lora-gw",
                        rssi = it.rssi ?: -80,
                        latitude = it.latitude ?: asset.latitude ?: 0.0,
                        longitude = it.longitude ?: asset.longitude ?: 0.0,
                        seenMacs = listOf(asset.mac)
                    )
                }
                Detection(
                    assetMac = asset.mac,
                    sourceType = DetectionSource.LORA,
                    nodeId = best.gatewayId ?: "lora-gateway",
                    rssi = best.rssi ?: -75,
                    latitude = best.latitude ?: asset.latitude,
                    longitude = best.longitude ?: asset.longitude,
                    accuracyMeters = best.accuracy ?: 100f,
                    message = "LoRa-Gateway: ${best.gatewayId ?: base}",
                    timestamp = Date()
                ).also { emit(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun searchHelium(asset: Asset): Detection? {
        val lat = asset.latitude ?: return null
        val lon = asset.longitude ?: return null
        val hotspots = try {
            heliumApi.getHotspots(lat, lon, limit = 10).data
        } catch (_: Exception) {
            emptyList()
        }
        gateways = hotspots.map { hs ->
            Gateway(
                id = hs.address ?: hs.name ?: "unknown",
                rssi = -70,
                latitude = hs.lat,
                longitude = hs.lng,
                seenMacs = emptyList()
            )
        }
        if (hotspots.isEmpty()) return null
        val nearest = hotspots.minByOrNull { hs ->
            val dLat = hs.lat - lat
            val dLon = hs.lng - lon
            dLat * dLat + dLon * dLon
        } ?: return null
        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.LORA,
            nodeId = nearest.address ?: "helium-hotspot",
            rssi = -70,
            latitude = nearest.lat,
            longitude = nearest.lng,
            accuracyMeters = 200f,
            message = "Helium Hotspot: ${nearest.name ?: nearest.address}",
            timestamp = Date()
        ).also { emit(it) }
    }

    suspend fun sendCommand(mac: String, command: String): Boolean {
        return try {
            mqttService.sendCommand(mac, command)
            mqttService.isConnected
        } catch (_: Exception) {
            false
        }
    }

    suspend fun refreshGateways(): List<Gateway> = gateways
}

data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)

data class LoraGatewaySighting(
    @SerializedName("gateway_id") val gatewayId: String? = null,
    @SerializedName("rssi") val rssi: Int? = null,
    @SerializedName("latitude") val latitude: Double? = null,
    @SerializedName("longitude") val longitude: Double? = null,
    @SerializedName("accuracy") val accuracy: Float? = null
)
