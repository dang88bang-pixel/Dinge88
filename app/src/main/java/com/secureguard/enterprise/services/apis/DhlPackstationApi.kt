package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * DHL/Post API – Paketstationen.
 *
 * Hinweis: Die öffentliche DHL-Business-API benötigt eine
 * Client-Credentials-Authentifizierung (kein freier Key). Der Endpunkt ist
 * daher als Vertrag hinterlegt und über den [ApiServiceManager]-Client
 * austauschbar (z. B. gegen den Sandbox-Endpunkt mit API-Key).
 */
interface DhlPackstationApi {

    @GET("packstation/api/v1/station")
    suspend fun getPackstations(
        @Query("lat") lat: Double,
        @Query("lng") lon: Double,
        @Query("radius") radius: Int = 1000
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
