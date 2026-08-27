package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * WiGle.net API – BSSID → GPS.
 *
 * Basis-URL: `https://api.wigle.net/` (Pfad `api/v2/network/search`).
 * WiGle authentifiziert per **HTTP-Basic** mit `API-Token:API-Name`.
 *
 * Format von `WIGLE_API_KEY` (siehe local.properties.example):
 *  - `<token>:<name>` → HTTP-Basic (empfohlen, so wie WiGle es verlangt)
 *  - beliebiger anderer Wert → `Authorization: Bearer <wert>` (Fallback für
 *    Proxy-/Enterprise-Gateways, die einen Bearer-Key erwarten)
 * Ohne gesetzten Key liefert der Aufruf leer (siehe [ApiServiceManager]).
 */
interface WiGleApi {

    @GET("api/v2/network/search")
    suspend fun searchBssid(
        @Query("netid") bssid: String,
        @Query("ssid") ssid: String? = null,
        @Header("Authorization") auth: String
    ): WiGleResponse
}

object WiGleAuth {
    /** Baut den Authorization-Header gemäß Key-Format (Basic/Bearer). */
    fun header(rawKey: String): String? {
        val key = rawKey.trim()
        if (key.isEmpty()) return null
        return if (key.contains(':')) {
            // HTTP-Basic: token:name (java.util.Base64, ab API 26 verfügbar)
            val encoded = java.util.Base64.getEncoder().encodeToString(key.toByteArray(Charsets.UTF_8))
            "Basic $encoded"
        } else {
            "Bearer $key"
        }
    }
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
