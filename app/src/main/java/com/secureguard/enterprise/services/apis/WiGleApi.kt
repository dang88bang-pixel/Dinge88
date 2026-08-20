package com.secureguard.enterprise.services.apis

import com.secureguard.enterprise.BuildConfig
import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * WiGle.net API – BSSID → GPS.
 *
 * Basis-URL: `https://api.wigle.net/` (Pfad `api/v2/network/search`).
 * WiGle authentifiziert per HTTP-Basic (API-Name:API-Token); über
 * `WIGLE_API_KEY` kann hier auch ein Bearer-Token hinterlegt werden.
 * Ohne gesetzten Key liefert der Aufruf leer (siehe [WiGleApi.searchBssid]).
 */
interface WiGleApi {

    @GET("api/v2/network/search")
    suspend fun searchBssid(
        @Query("netid") bssid: String,
        @Query("ssid") ssid: String? = null,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.WIGLE_API_KEY}"
    ): WiGleResponse
}

data class WiGleResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "results") val results: List<WiGleResult> = emptyList()
)

data class WiGleResult(
    @Json(name = "trilat") val trilat: Double? = null,
    @Json(name = "trilong") val trilong: Double? = null,
    @Json(name = "ssid") val ssid: String? = null,
    @Json(name = "netid") val bssid: String? = null,
    @Json(name = "firsttime") val firstSeen: String? = null,
    @Json(name = "lasttime") val lastSeen: String? = null
)
