package com.secureguard.enterprise.services.apis

import com.squareup.moshi.Json
import io.reactivex.rxjava3.core.Single
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Charge Map API – Ladesäulen in der Nähe.
 *
 * Basis-URL: `https://api.openchargemap.io/`, Pfad `v3/poi`.
 * Antwort ist ein JSON-Array von POIs – das DTO bildet die reale
 * (verschachtelte) Antwortstruktur ab.
 *
 * Hinweis Konsolidierung (F-27): Die Retrofit-Schicht nutzt ausschließlich
 * Moshi (siehe ApiServiceManager); die DTOs sind daher mit `@Json`
 * annotiert. Gson bleibt nur für App-interne Services im Einsatz.
 */
interface OpenChargeMapApi {

    @GET("v3/poi")
    suspend fun getStations(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("distance") distance: Int = 1000,
        @Query("maxresults") maxResults: Int = 10,
        @Query("compact") compact: Boolean = true,
        @Query("verbose") verbose: Boolean = false,
        @Query("key") apiKey: String
    ): List<ChargingStation>
}

/**
 * RxJava-Variante derselben Abfrage (nicht-suspend), für Aufrufer außerhalb
 * von Coroutines. Wird vom ApiServiceManager über die geteilte
 * Retrofit-Instanz (gleicher Converter + RxJava3-Adapter) aufgelöst.
 */
interface OpenChargeMapRxApi {

    @GET("v3/poi")
    fun getStations(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("distance") distance: Int = 1000,
        @Query("maxresults") maxResults: Int = 10,
        @Query("compact") compact: Boolean = true,
        @Query("verbose") verbose: Boolean = false,
        @Query("key") apiKey: String
    ): Single<List<ChargingStation>>
}

data class ChargingStation(
    @Json(name = "ID") val id: Long = 0,
    @Json(name = "Title") val title: String? = null,
    @Json(name = "AddressInfo") val addressInfo: ChargingStationAddress? = null,
    @Json(name = "OperatorInfo") val operatorInfo: ChargingStationOperator? = null,
    @Json(name = "UsageType") val usageType: ChargingStationUsage? = null,
    @Json(name = "StatusType") val statusType: ChargingStationStatus? = null
) {
    val latitude: Double? get() = addressInfo?.latitude
    val longitude: Double? get() = addressInfo?.longitude
    val address: String? get() = addressInfo?.addressLine1
    val operator: String? get() = operatorInfo?.title
    val usage: String? get() = usageType?.title
    val status: String? get() = statusType?.title
}

data class ChargingStationAddress(
    @Json(name = "Title") val title: String? = null,
    @Json(name = "AddressLine1") val addressLine1: String? = null,
    @Json(name = "Town") val town: String? = null,
    @Json(name = "Latitude") val latitude: Double? = null,
    @Json(name = "Longitude") val longitude: Double? = null
)

data class ChargingStationOperator(
    @Json(name = "Title") val title: String? = null
)

data class ChargingStationUsage(
    @Json(name = "Title") val title: String? = null
)

data class ChargingStationStatus(
    @Json(name = "Title") val title: String? = null
)
