package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.services.apis.ChargingStation
import com.secureguard.enterprise.services.apis.CkanDataset
import com.secureguard.enterprise.services.apis.CkanOpenDataApi
import com.secureguard.enterprise.services.apis.DhlPackstationApi
import com.secureguard.enterprise.services.apis.GeolocationLocation
import com.secureguard.enterprise.services.apis.GeolocationRequest
import com.secureguard.enterprise.services.apis.GoogleGeolocationApi
import com.secureguard.enterprise.services.apis.HeliumHotspot
import com.secureguard.enterprise.services.apis.HeliumNetworkApi
import com.secureguard.enterprise.services.apis.MacLookupApi
import com.secureguard.enterprise.services.apis.NetatmoDevice
import com.secureguard.enterprise.services.apis.NetatmoWeatherApi
import com.secureguard.enterprise.services.apis.OpenChargeMapApi
import com.secureguard.enterprise.services.apis.OpenChargeMapRxApi
import com.secureguard.enterprise.services.apis.Packstation
import com.secureguard.enterprise.services.apis.WiGleApi
import com.secureguard.enterprise.services.apis.WifiAccessPoint
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Verwaltung aller externen REST-APIs (WiGle, MacLookup,
 * OpenChargeMap, DHL, CKAN, Google Geolocation, Netatmo, Helium).
 *
 * Jede Methode ist fehlertolerant: Bei fehlenden API-Keys, fehlendem Netz
 * oder Serverfehlern wird `null` / eine leere Liste zurückgegeben – der
 * Agent bleibt damit stabil. WiGle-Treffer werden zusätzlich über
 * [detections] als [DetectionSource.API]-Flow emittiert.
 */
@Singleton
class ApiServiceManager @Inject constructor(
    private val cacheManager: com.secureguard.enterprise.util.CacheManager
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (com.secureguard.enterprise.BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .build()
    }

    /** Moshi mit Kotlin-Reflektion (nutzt die @Json-Annotationen der DTOs). */
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private fun retrofitBuilder(baseUrl: String, moshi: Boolean = false): Retrofit.Builder =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(
                if (moshi) MoshiConverterFactory.create(this.moshi)
                else GsonConverterFactory.create()
            )

    // ============ API-CLIENTS ============
    private val wigleApi: WiGleApi by lazy {
        retrofitBuilder("https://api.wigle.net/").build().create(WiGleApi::class.java)
    }

    private val macLookupApi: MacLookupApi by lazy {
        retrofitBuilder("https://api.maclookup.app/", moshi = true)
            .build()
            .create(MacLookupApi::class.java)
    }

    private val openChargeMapApi: OpenChargeMapApi by lazy {
        retrofitBuilder("https://api.openchargemap.io/").build().create(OpenChargeMapApi::class.java)
    }

    private val dhlApi: DhlPackstationApi by lazy {
        retrofitBuilder("https://api.dhl.de/").build().create(DhlPackstationApi::class.java)
    }

    private val ckanApi: CkanOpenDataApi by lazy {
        retrofitBuilder("https://demo.ckan.org/").build().create(CkanOpenDataApi::class.java)
    }

    private val googleGeolocationApi: GoogleGeolocationApi by lazy {
        retrofitBuilder("https://www.googleapis.com/").build().create(GoogleGeolocationApi::class.java)
    }

    private val netatmoApi: NetatmoWeatherApi by lazy {
        retrofitBuilder("https://api.netatmo.com/").build().create(NetatmoWeatherApi::class.java)
    }

    private val heliumApi: HeliumNetworkApi by lazy {
        retrofitBuilder("https://api.helium.io/", moshi = true)
            .build()
            .create(HeliumNetworkApi::class.java)
    }

    /** RxJava-aktivierter Client (für asynchrone Aufrufe außerhalb von Coroutines). */
    private val rxChargeMapApi: OpenChargeMapRxApi by lazy {
        retrofitBuilder("https://api.openchargemap.io/")
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build()
            .create(OpenChargeMapRxApi::class.java)
    }

    // ============ DETECTION-FLOW ============
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    // ============ API-ABFRAGEN ============

    /** WiGle.net: BSSID → GPS. Liefert eine [Detection] (Source API) oder null. */
    suspend fun searchViaWiGle(bssid: String): Detection? {
        if (com.secureguard.enterprise.BuildConfig.WIGLE_API_KEY.isBlank()) return null
        // Check cache first
        cacheManager.get<Detection>("wigle_$bssid")?.let { return it }
        return com.secureguard.enterprise.util.RetryManager.withRetryOrNull(maxAttempts = 2) {
            val response = wigleApi.searchBssid(bssid)
            val result = response.results.firstOrNull()
            if (result != null && result.trilat != null && result.trilong != null) {
                val detection = Detection(
                    assetMac = bssid,
                    sourceType = DetectionSource.API,
                    nodeId = result.bssid,
                    rssi = 0,
                    latitude = result.trilat,
                    longitude = result.trilong,
                    accuracyMeters = 100f,
                    message = "WiGle-Treffer: ${result.ssid ?: "unbekannt"}",
                    timestamp = Date()
                )
                _detections.tryEmit(detection)
                cacheManager.put("wigle_$bssid", detection)
                detection
            } else null
        }
        } catch (e: Exception) {
            null
        }
    }

    /** MacLookup.app: MAC → Hersteller (OUI-Auflösung). */
    suspend fun searchViaMacLookup(mac: String): String? {
        cacheManager.get<String>("mac_$mac")?.let { return it }
        val vendor = com.secureguard.enterprise.util.RetryManager.withRetryOrNull(maxAttempts = 2) {
            macLookupApi.lookupMac(mac).vendor
        }
        if (vendor != null) cacheManager.put("mac_$mac", vendor)
        return vendor
    }

    /** Open Charge Map: Ladesäulen um eine Position. */
    suspend fun searchViaOpenChargeMap(lat: Double, lon: Double): List<ChargingStation> {
        val key = com.secureguard.enterprise.BuildConfig.OPEN_CHARGE_MAP_KEY
        if (key.isBlank()) return emptyList()
        return try {
            openChargeMapApi.getStations(lat, lon, apiKey = key)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** RxJava-Variante der Open-Charge-Map-Abfrage. */
    fun searchViaOpenChargeMapRx(lat: Double, lon: Double): Single<List<ChargingStation>> {
        val key = com.secureguard.enterprise.BuildConfig.OPEN_CHARGE_MAP_KEY
        return rxChargeMapApi.getStations(lat, lon, apiKey = key)
            .onErrorReturn { emptyList() }
    }

    /** DHL: Paketstationen um eine Position. */
    suspend fun searchViaDHL(lat: Double, lon: Double): List<Packstation> {
        return try {
            dhlApi.getPackstations(lat, lon)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** CKAN: Open-Data-Datensätze durchsuchen (Smart City). */
    suspend fun searchViaCKAN(query: String): List<CkanDataset> {
        return try {
            ckanApi.searchDatasets(query).result.results
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Google Geolocation: WLAN-Access-Points → Position. */
    suspend fun searchViaGoogleGeolocation(accessPoints: List<WifiAccessPoint>): GeolocationLocation? {
        val key = com.secureguard.enterprise.BuildConfig.GOOGLE_API_KEY
        if (key.isBlank() || accessPoints.isEmpty()) return null
        return try {
            googleGeolocationApi.geolocate(
                GeolocationRequest(wifiAccessPoints = accessPoints),
                key
            ).location
        } catch (e: Exception) {
            null
        }
    }

    /** Netatmo: Wetterstationen des Accounts. */
    suspend fun searchViaNetatmo(stationId: String? = null): List<NetatmoDevice> {
        if (com.secureguard.enterprise.BuildConfig.NETATMO_TOKEN.isBlank()) return emptyList()
        return try {
            netatmoApi.getStations(stationId).body.devices
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Helium: LoRaWAN-Hotspots um eine Position. */
    suspend fun searchViaHelium(lat: Double, lon: Double): List<HeliumHotspot> {
        return try {
            heliumApi.getHotspots(lat, lon).data
        } catch (e: Exception) {
            emptyList()
        }
    }
}
