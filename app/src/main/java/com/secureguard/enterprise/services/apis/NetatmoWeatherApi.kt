package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Netatmo Weathermap API – Wetterstationen.
 *
 * Basis-URL: `https://api.netatmo.com/`, Pfad `api/getstationsdata`.
 * Authentifizierung per Bearer-Token (Netatmo OAuth2), siehe
 * `BuildConfig.NETATMO_TOKEN`.
 */
interface NetatmoWeatherApi {

    @GET("api/getstationsdata")
    suspend fun getStations(
        @Query("device_id") deviceId: String? = null,
        @Header("Authorization") auth: String = "Bearer ${com.secureguard.enterprise.BuildConfig.NETATMO_TOKEN}"
    ): NetatmoResponse
}

data class NetatmoResponse(
    @Json(name = "body") val body: NetatmoBody = NetatmoBody()
)

data class NetatmoBody(
    @Json(name = "devices") val devices: List<NetatmoDevice> = emptyList()
)

data class NetatmoDevice(
    @Json(name = "_id") val id: String? = null,
    @Json(name = "station_name") val stationName: String? = null,
    @Json(name = "place") val place: NetatmoPlace? = null,
    @Json(name = "dashboard_data") val dashboardData: NetatmoDashboard? = null
)

data class NetatmoPlace(
    @Json(name = "latitude") val latitude: Double = 0.0,
    @Json(name = "longitude") val longitude: Double = 0.0
)

data class NetatmoDashboard(
    @Json(name = "Temperature") val temperature: Double? = null,
    @Json(name = "Humidity") val humidity: Int? = null,
    @Json(name = "Pressure") val pressure: Double? = null,
    @Json(name = "CO2") val co2: Int? = null,
    @Json(name = "Noise") val noise: Int? = null
)
