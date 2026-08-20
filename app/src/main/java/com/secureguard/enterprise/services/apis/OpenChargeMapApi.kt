package com.secureguard.enterprise.services.apis

import com.google.gson.annotations.SerializedName
import io.reactivex.rxjava3.core.Single
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open Charge Map API – Ladesäulen in der Nähe.
 *
 * Basis-URL: `https://api.openchargemap.io/`, Pfad `v3/poi`.
 * Antwort ist ein JSON-Array von POIs – das DTO bildet die reale
 * (verschachtelte) Antwortstruktur ab.
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
 * von Coroutines. Wird vom ApiServiceManager über den RxJava3-Call-Adapter
 * aufgelöst.
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
    @SerializedName("ID") val id: Long = 0,
    @SerializedName("Title") val title: String? = null,
    @SerializedName("AddressInfo") val addressInfo: ChargingStationAddress? = null,
    @SerializedName("OperatorInfo") val operatorInfo: ChargingStationOperator? = null,
    @SerializedName("UsageType") val usageType: ChargingStationUsage? = null,
    @SerializedName("StatusType") val statusType: ChargingStationStatus? = null
) {
    val latitude: Double? get() = addressInfo?.latitude
    val longitude: Double? get() = addressInfo?.longitude
    val address: String? get() = addressInfo?.addressLine1
    val operator: String? get() = operatorInfo?.title
    val usage: String? get() = usageType?.title
    val status: String? get() = statusType?.title
}

data class ChargingStationAddress(
    @SerializedName("Title") val title: String? = null,
    @SerializedName("AddressLine1") val addressLine1: String? = null,
    @SerializedName("Town") val town: String? = null,
    @SerializedName("Latitude") val latitude: Double? = null,
    @SerializedName("Longitude") val longitude: Double? = null
)

data class ChargingStationOperator(
    @SerializedName("Title") val title: String? = null
)

data class ChargingStationUsage(
    @SerializedName("Title") val title: String? = null
)

data class ChargingStationStatus(
    @SerializedName("Title") val title: String? = null
)
