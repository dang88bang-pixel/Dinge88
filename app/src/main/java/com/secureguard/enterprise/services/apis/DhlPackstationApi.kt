package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * DHL Location Finder API – Paketstationen/Poststellen in der Nähe.
 *
 * Echter Endpunkt (developer.dhl.com):
 * `GET https://api.dhl.com/location-finder/v1/findLocations`
 * mit Header `DHL-API-Key` (OAuth2-Client-Credentials beim DHL-Developer-
 * Portal). Der Key wird über `DHL_API_KEY` (gradle.properties /
 * local.properties) konfiguriert; ohne Key liefert der Manager eine leere
 * Liste (kein Fake).
 */
interface DhlPackstationApi {

    @GET("location-finder/v1/findLocations")
    suspend fun getPackstations(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("radiusMeters") radius: Int = 1000,
        @Query("locationType") locationType: String = "locker",
        @Header("DHL-API-Key") apiKey: String
    ): DhlLocationResponse
}

/** Antwort der Location-Finder-API (`{"locations":[...]}`). */
data class DhlLocationResponse(
    @Json(name = "locations") val locations: List<DhlLocation> = emptyList()
)

data class DhlLocation(
    @Json(name = "superApiId") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "distance") val distanceMeters: Double? = null,
    @Json(name = "place") val place: DhlPlace? = null
) {
    val latitude: Double? get() = place?.address?.geo?.latitude
    val longitude: Double? get() = place?.address?.geo?.longitude
    val address: String? get() = place?.address?.streetAddress
}

data class DhlPlace(
    @Json(name = "address") val address: DhlAddress? = null,
    @Json(name = "location") val location: DhlLocationDetails? = null
)

data class DhlAddress(
    @Json(name = "streetAddress") val streetAddress: String? = null,
    @Json(name = "postalCode") val postalCode: String? = null,
    @Json(name = "addressLocality") val addressLocality: String? = null,
    @Json(name = "geo") val geo: DhlGeo? = null
)

data class DhlGeo(
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null
)

data class DhlLocationDetails(
    @Json(name = "id") val id: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "description") val description: String? = null
)

/** Abwärtskompatibles UI-Modell (Ladesäulen-/Paketstation-Karten). */
data class Packstation(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "address") val address: String? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "boxesAvailable") val boxesAvailable: Int = 0
) {
    companion object {
        fun from(location: DhlLocation): Packstation = Packstation(
            id = location.id,
            name = location.name,
            address = location.address,
            latitude = location.latitude,
            longitude = location.longitude
        )
    }
}
