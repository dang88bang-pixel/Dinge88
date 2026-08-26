package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.services.apis.HeliumNetworkApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic LoRa / LoRaWAN service — replaces the former Meshtastic dependency.
 *
 * Uses the Helium Network API to discover LoRaWAN hotspots near the asset's
 * last known position. Commands are sent via MQTT to the ESP32 gateway which
 * relays them over LoRa to the asset.
 *
 * Returns null when no hotspots are found near the asset or the API is
 * unreachable. No simulated data is ever generated.
 */
@Singleton
class LoraService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttService: MqttService
) : DetectionCapable() {

    private val heliumApi: HeliumNetworkApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.helium.io/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HeliumNetworkApi::class.java)
    }

    @Volatile
    var gateways: List<Gateway> = emptyList()
        private set

    suspend fun searchAsset(asset: Asset): Detection? {
        val lat = asset.latitude ?: return null
        val lon = asset.longitude ?: return null

        val hotspots = try {
            heliumApi.getHotspots(lat, lon, limit = 10).data
        } catch (e: Exception) {
            emptyList()
        }

        // Helium-API liefert nur Hotspot-Positionen, keinen RSSI- oder
        // Genauigkeitswert des gesuchten Assets. Dementsprechend bleiben beide
        // leer bzw. UNKNOWN (rssi=0, accuracy=null) statt künstlicher Werte.
        gateways = hotspots.map { hs ->
            Gateway(
                id = hs.address ?: hs.name ?: "unknown",
                rssi = 0,
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
            rssi = 0,
            latitude = nearest.lat,
            longitude = nearest.lng,
            accuracyMeters = null,
            message = "Helium Hotspot: ${nearest.name ?: nearest.address}",
            timestamp = Date()
        ).also { emit(it) }
    }

    /**
     * Sends a command to an asset via MQTT. The ESP32 gateway receives the
     * MQTT message and relays it over LoRa to the asset.
     */
    suspend fun sendCommand(mac: String, command: String): Boolean {
        return try {
            mqttService.sendCommand(mac, command)
            mqttService.isConnected
        } catch (e: Exception) {
            false
        }
    }

    suspend fun refreshGateways(): List<Gateway> {
        return gateways
    }
}

/** A LoRa gateway that recently reported sightings. */
data class Gateway(
    val id: String,
    val rssi: Int,
    val latitude: Double,
    val longitude: Double,
    val seenMacs: List<String>
)
