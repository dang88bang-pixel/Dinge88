package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * DHL/Post API – Paketstationen.
 *
 * Hinweis: Die DHL-API benötigt einen Vertrag (OAuth2-Client-Credentials
 * bzw. Sandbox-Key). Der Kanal ist dadurch voll **wire-ready**:
 *  - Basis-URL: zur Laufzeit austauschbar ([com.secureguard.enterprise.config.EndpointConfig.dhlApiUrl],
 *    Default/BuildConfig `DHL_API_URL`)
 *  - Auth: optionaler Bearer-Token ([com.secureguard.enterprise.config.EndpointConfig.dhlApiToken],
 *    Default/BuildConfig `DHL_API_TOKEN`); ohne Token wird kein Header gesetzt
 * Ohne Vertrag liefert der Aufruf konsequent eine leere Liste.
 */
interface DhlPackstationApi {

    @GET("packstation/api/v1/station")
    suspend fun getPackstations(
        @Query("lat") lat: Double,
        @Query("lng") lon: Double,
        @Query("radius") radius: Int = 1000,
        @Header("Authorization") auth: String? = null
    ): List<Packstation>
}

data class Packstation(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "boxesAvailable") val boxesAvailable: Int = 0
)
