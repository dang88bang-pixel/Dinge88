package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * MacLookup.app API – OUI-Auflösung (MAC → Hersteller).
 *
 * Basis-URL: `https://api.maclookup.app/`, Pfad `v2/macs/{mac}`.
 * Kostenlos ohne Key; private MACs werden als solche gemeldet.
 */
interface MacLookupApi {

    @GET("v2/macs/{mac}")
    suspend fun lookupMac(@Path("mac") mac: String): MacLookupResponse
}

data class MacLookupResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "found") val found: Boolean = false,
    @Json(name = "mac") val mac: String? = null,
    @Json(name = "company") val vendor: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "country") val country: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "isPrivate") val isPrivate: Boolean = false
)
