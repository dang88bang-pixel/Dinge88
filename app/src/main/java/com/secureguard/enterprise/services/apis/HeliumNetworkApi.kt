package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Helium Network API – LoRaWAN-Hotspots in der Nähe.
 *
 * Basis-URL: `https://api.helium.io/`, Pfad `v1/hotspots/lat/{lat}/lon/{lon}`
 * (Hotspots um eine Position) bzw. `v1/hotspots/{id}/beacons`.
 */
interface HeliumNetworkApi {

    @GET("v1/hotspots/lat/{lat}/lon/{lon}")
    suspend fun getHotspots(
        @Path("lat") lat: Double,
        @Path("lon") lon: Double,
        @Query("limit") limit: Int = 10
    ): HeliumHotspotResponse

    @GET("v1/hotspots/{hotspotId}/beacons")
    suspend fun getBeacons(
        @Path("hotspotId") hotspotId: String,
        @Query("limit") limit: Int = 20
    ): HeliumBeaconResponse
}

data class HeliumHotspotResponse(
    @Json(name = "data") val data: List<HeliumHotspot> = emptyList()
)

data class HeliumHotspot(
    @Json(name = "address") val address: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "lat") val lat: Double = 0.0,
    @Json(name = "lng") val lng: Double = 0.0,
    @Json(name = "status") val status: String? = null,
    @Json(name = "reward_scale") val rewardScale: Double? = null
)

data class HeliumBeaconResponse(
    @Json(name = "data") val data: List<HeliumBeacon> = emptyList()
)

data class HeliumBeacon(
    @Json(name = "hotspot") val hotspotId: String? = null,
    @Json(name = "time") val timestamp: String? = null,
    @Json(name = "payload") val payload: String? = null,
    @Json(name = "rssi") val rssi: Int = 0,
    @Json(name = "snr") val snr: Double = 0.0
)
