package com.secureguard.enterprise.services

import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.config.EndpointConfig
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
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
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
 *
 * Konsolidierung (F-27/F-57): Die komplette Retrofit-Schicht nutzt **nur
 * Moshi** (inkl. OpenChargeMap + Google Geolocation); Gson bleibt nur für
 * App-interne Services (Sync/MQTT/WS/MCP). OpenChargeMap suspend- und
 * Rx-Variante teilen sich eine Retrofit-Instanz.
 */
@Singleton
class ApiServiceManager @Inject constructor(
    private val cacheManager: com.secureguard.enterprise.util.CacheManager,
    private val endpointConfig: EndpointConfig
) {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
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

    private fun retrofitBuilder(baseUrl: String): Retrofit.Builder =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))

    // ============ API-CLIENTS ============
    private val wigleApi: WiGleApi by lazy {
        retrofitBuilder("https://api.wigle.net/").build().create(WiGleApi::class.java)
    }

    private val macLookupApi: MacLookupApi by lazy {
        retrofitBuilder("https://api.maclookup.app/").build().create(MacLookupApi::class.java)
    }

    /** Geteilte OCM-Retrofit-Instanz: suspend- + Rx-Interface (F-57). */
    private val openChargeMapRetrofit: Retrofit by lazy {
        retrofitBuilder("https://api.openchargemap.io/")
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build()
    }

    private val openChargeMapApi: OpenChargeMapApi by lazy {
        openChargeMapRetrofit.create(OpenChargeMapApi::class.java)
    }

    private val rxChargeMapApi: OpenChargeMapRxApi by lazy {
        openChargeMapRetrofit.create(OpenChargeMapRxApi::class.java)
    }

    private val dhlApi: DhlPackstationApi by lazy {
        retrofitBuilder(endpointConfig.dhlApiUrl.ifBlank { "https://api.dhl.de/" })
            .build()
            .create(DhlPackstationApi::class.java)
    }

    private val ckanApi: CkanOpenDataApi by lazy {
        val base = endpointConfig.openDataApiUrl.ifBlank { "https://demo.ckan.org/" }
        retrofitBuilder(base).build().create(CkanOpenDataApi::class.java)
    }

    private val googleGeolocationApi: GoogleGeolocationApi by lazy {
        retrofitBuilder("https://www.googleapis.com/").build().create(GoogleGeolocationApi::class.java)
    }

    private val netatmoApi: NetatmoWeatherApi by lazy {
        retrofitBuilder("https://api.netatmo.com/").build().create(NetatmoWeatherApi::class.java)
    }

    private val heliumApi: HeliumNetworkApi by lazy {
        retrofitBuilder(HELIUM_BASE_URL).build().create(HeliumNetworkApi::class.java)
    }

    // ============ NETATMO OAUTH2 (F-19) ============

    private data class NetatmoToken(val accessToken: String, val expiresAtMs: Long)

    @Volatile
    private var netatmoToken: NetatmoToken? = null

    private val netatmoMutex = Mutex()

    /**
     * Liefert einen gültigen `Authorization`-Header für Netatmo.
     *
     * 1. Mit `NETATMO_CLIENT_ID/SECRET/REFRESH_TOKEN`: automatischer
     *    OAuth2-Refresh (`POST /oauth2/token`), Token wird mit Puffer
     *    (60 s) gecacht.
     * 2. Nur mit legacy `NETATMO_TOKEN`: statischer Bearer (verfällt nach ~3 h).
     * 3. Ohne Konfiguration: null → Kanal liefert leer.
     */
    private suspend fun netatmoAuthHeader(): String? {
        val staticToken = BuildConfig.NETATMO_TOKEN
        val hasOAuth = BuildConfig.NETATMO_CLIENT_ID.isNotBlank() &&
            BuildConfig.NETATMO_CLIENT_SECRET.isNotBlank() &&
            BuildConfig.NETATMO_REFRESH_TOKEN.isNotBlank()
        if (!hasOAuth) {
            return if (staticToken.isBlank()) null else "Bearer $staticToken"
        }
        return netatmoMutex.withLock {
            val cached = netatmoToken
            if (cached != null && System.currentTimeMillis() < cached.expiresAtMs - 60_000L) {
                return@withLock "Bearer ${cached.accessToken}"
            }
            val refreshed = withContext(Dispatchers.IO) {
                refreshNetatmoToken(
                    clientId = BuildConfig.NETATMO_CLIENT_ID,
                    clientSecret = BuildConfig.NETATMO_CLIENT_SECRET,
                    refreshToken = BuildConfig.NETATMO_REFRESH_TOKEN
                )
            }
            refreshed?.let { "Bearer $it" }
        }
    }

    private fun refreshNetatmoToken(clientId: String, clientSecret: String, refreshToken: String): String? {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .build()
        val request = okhttp3.Request.Builder()
            .url("https://api.netatmo.com/oauth2/token")
            .post(form)
            .build()
        return try {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JSONObject(body)
                val access = json.optString("access_token", "")
                val expiresIn = json.optInt("expire_in", 3 * 60 * 60)
                if (access.isBlank()) return null
                netatmoToken = NetatmoToken(access, System.currentTimeMillis() + expiresIn * 1000L)
                access
            }
        } catch (_: Exception) {
            null
        }
    }

    // ============ DETECTION-FLOW ============
    private val _detections = kotlinx.coroutines.flow.MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections: kotlinx.coroutines.flow.SharedFlow<Detection> = _detections

    // ============ API-ABFRAGEN ============

    /** WiGle.net: BSSID → GPS. Liefert eine [Detection] (Source API) oder null. */
    suspend fun searchViaWiGle(bssid: String): Detection? {
        val authHeader = com.secureguard.enterprise.services.apis.WiGleAuth.header(
            BuildConfig.WIGLE_API_KEY
        ) ?: return null
        // Check cache first
        cacheManager.get<Detection>("wigle_$bssid")?.let { return it }
        return com.secureguard.enterprise.util.RetryManager.withRetryOrNull(maxAttempts = 2) {
            val response = wigleApi.searchBssid(bssid, auth = authHeader)
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
                    timestamp = java.util.Date()
                )
                _detections.tryEmit(detection)
                cacheManager.put("wigle_$bssid", detection)
                detection
            } else null
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
        val key = BuildConfig.OPEN_CHARGE_MAP_KEY
        if (key.isBlank()) return emptyList()
        return try {
            openChargeMapApi.getStations(lat, lon, apiKey = key)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** RxJava-Variante der Open-Charge-Map-Abfrage. */
    fun searchViaOpenChargeMapRx(lat: Double, lon: Double): Single<List<ChargingStation>> {
        val key = BuildConfig.OPEN_CHARGE_MAP_KEY
        return rxChargeMapApi.getStations(lat, lon, apiKey = key)
            .onErrorReturn { emptyList() }
    }

    /**
     * DHL: Paketstationen um eine Position. Basis-URL + optionaler
     * Bearer-Token kommen zur Laufzeit aus [EndpointConfig] (DHL-Vertrag
     * nötig, siehe IMPLEMENTIERUNGS_INVENTUR.md §3).
     */
    suspend fun searchViaDHL(lat: Double, lon: Double): List<Packstation> {
        val token = endpointConfig.dhlApiToken
        return try {
            dhlApi.getPackstations(lat, lon, auth = token.takeIf { it.isNotBlank() }?.let { "Bearer $it" })
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
        val key = BuildConfig.GOOGLE_API_KEY
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

    /** Netatmo: Wetterstationen des Accounts (mit OAuth2-Refresh, F-19). */
    suspend fun searchViaNetatmo(stationId: String? = null): List<NetatmoDevice> {
        val auth = netatmoAuthHeader() ?: return emptyList()
        return try {
            netatmoApi.getStations(stationId, auth).body.devices
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

    companion object {
        /**
         * Helium IoT-API (F-20): Nach der Solana-Migration teils verändert/deprecated.
         * Basis-URL zentral, im Fehlerfall liefert der Kanal leer.
         */
        const val HELIUM_BASE_URL = "https://api.helium.io/"
    }
}
