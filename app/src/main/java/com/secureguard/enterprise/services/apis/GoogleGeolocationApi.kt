package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Geolocation API – WLAN-/Funkmast-Triangulation.
 *
 * Basis-URL: `https://www.googleapis.com/`, Pfad `geolocation/v1/geolocate`.
 * Der API-Key wird als Query-Parameter übergeben.
 *
 * Hinweis Konsolidierung (F-27): DTOs mit Moshi-`@Json` (Retrofit-Schicht
 * ist Moshi-only, siehe ApiServiceManager).
 */
interface GoogleGeolocationApi {

    @POST("geolocation/v1/geolocate")
    suspend fun geolocate(
        @Body request: GeolocationRequest,
        @Query("key") apiKey: String
    ): GeolocationResponse
}

data class GeolocationRequest(
    @Json(name = "wifiAccessPoints") val wifiAccessPoints: List<WifiAccessPoint>? = null,
    @Json(name = "cellTowers") val cellTowers: List<CellTower>? = null,
    @Json(name = "considerIp") val considerIp: Boolean = true
)

data class WifiAccessPoint(
    @Json(name = "macAddress") val macAddress: String,
    @Json(name = "signalStrength") val signalStrength: Int = 0,
    @Json(name = "age") val age: Int? = null,
    @Json(name = "channel") val channel: Int? = null,
    @Json(name = "signalToNoiseRatio") val signalToNoiseRatio: Int? = null
)

data class CellTower(
    @Json(name = "cellId") val cellId: Long,
    @Json(name = "locationAreaCode") val locationAreaCode: Int,
    @Json(name = "mobileCountryCode") val mobileCountryCode: Int,
    @Json(name = "mobileNetworkCode") val mobileNetworkCode: Int,
    @Json(name = "signalStrength") val signalStrength: Int = 0,
    @Json(name = "age") val age: Int? = null
)

data class GeolocationResponse(
    @Json(name = "location") val location: GeolocationLocation? = null,
    @Json(name = "accuracy") val accuracy: Double = 0.0
)

data class GeolocationLocation(
    @Json(name = "lat") val lat: Double = 0.0,
    @Json(name = "lng") val lng: Double = 0.0
)
