package com.secureguard.enterprise.services.apis

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Google Geolocation API – WLAN-/Funkmast-Triangulation.
 *
 * Basis-URL: `https://www.googleapis.com/`, Pfad `geolocation/v1/geolocate`.
 * Der API-Key wird als Query-Parameter übergeben.
 */
interface GoogleGeolocationApi {

    @POST("geolocation/v1/geolocate")
    suspend fun geolocate(
        @Body request: GeolocationRequest,
        @Query("key") apiKey: String
    ): GeolocationResponse
}

data class GeolocationRequest(
    @SerializedName("wifiAccessPoints") val wifiAccessPoints: List<WifiAccessPoint>? = null,
    @SerializedName("cellTowers") val cellTowers: List<CellTower>? = null,
    @SerializedName("considerIp") val considerIp: Boolean = true
)

data class WifiAccessPoint(
    @SerializedName("macAddress") val macAddress: String,
    /** Null = weglassen (reine BSSID-Suche ohne erfundene Signalstärke). */
    @SerializedName("signalStrength") val signalStrength: Int? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("channel") val channel: Int? = null,
    @SerializedName("signalToNoiseRatio") val signalToNoiseRatio: Int? = null
)

data class CellTower(
    @SerializedName("cellId") val cellId: Long,
    @SerializedName("locationAreaCode") val locationAreaCode: Int,
    @SerializedName("mobileCountryCode") val mobileCountryCode: Int,
    @SerializedName("mobileNetworkCode") val mobileNetworkCode: Int,
    @SerializedName("signalStrength") val signalStrength: Int = 0,
    @SerializedName("age") val age: Int? = null
)

data class GeolocationResponse(
    @SerializedName("location") val location: GeolocationLocation? = null,
    @SerializedName("accuracy") val accuracy: Double = 0.0
)

data class GeolocationLocation(
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lng") val lng: Double = 0.0
)
