package com.secureguard.enterprise.config

import android.content.Context
import android.content.SharedPreferences
import com.secureguard.enterprise.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Laufzeit-Konfiguration für Backend-/Broker-Endpunkte.
 *
 * Priorität: **SharedPreferences (Settings-UI)** > **BuildConfig** (local.properties)
 * > hart codierter Emulator-Default (nur MQTT).
 *
 * So können Pilot-Installationen URLs ohne Rebuild anpassen.
 */
@Singleton
class EndpointConfig @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- MQTT ----

    /** Paho-kompatible Broker-URI (`tcp://` / `ssl://`). */
    val mqttBrokerUrl: String
        get() = normalizeMqtt(
            prefs.getString(KEY_MQTT_URL, null).orEmpty()
                .ifBlank { BuildConfig.MQTT_BROKER_URL }
                .ifBlank { DEFAULT_MQTT }
        )

    val mqttUsername: String
        get() = prefs.getString(KEY_MQTT_USER, null).orEmpty()
            .ifBlank { BuildConfig.MQTT_USERNAME }

    val mqttPassword: String
        get() = prefs.getString(KEY_MQTT_PASS, null).orEmpty()
            .ifBlank { BuildConfig.MQTT_PASSWORD }

    // ---- Backend / Realtime ----

    val websocketUrl: String
        get() = prefs.getString(KEY_WS_URL, null).orEmpty()
            .ifBlank { BuildConfig.WEBSOCKET_URL }

    val mcpServerUrl: String
        get() = prefs.getString(KEY_MCP_URL, null).orEmpty()
            .ifBlank { BuildConfig.MCP_SERVER_URL }

    /**
     * HTTP-Basis des SecureGuard-Backends (Assets-Sync, Crowd, MCP-REST).
     * Abgeleitet aus BACKEND_BASE_URL, sonst aus WEBSOCKET_URL.
     */
    val backendBaseUrl: String
        get() {
            val explicit = prefs.getString(KEY_BACKEND_URL, null).orEmpty()
                .ifBlank { BuildConfig.BACKEND_BASE_URL }
            if (explicit.isNotBlank()) return explicit.trimEnd('/')
            return deriveHttpBase(websocketUrl)
        }

    val loraGatewayUrl: String
        get() = prefs.getString(KEY_LORA_URL, null).orEmpty()
            .ifBlank { BuildConfig.LORA_GATEWAY_URL }
            .trimEnd('/')

    val yoloServerUrl: String
        get() = prefs.getString(KEY_YOLO_URL, null).orEmpty()
            .ifBlank { BuildConfig.YOLO_SERVER_URL }
            .trimEnd('/')

    val openDataApiUrl: String
        get() = prefs.getString(KEY_CKAN_URL, null).orEmpty()
            .ifBlank { BuildConfig.OPEN_DATA_API_URL }
            .ifBlank { DEFAULT_CKAN }
            .let { ensureTrailingSlash(it) }

    val findMyProxyUrl: String
        get() = prefs.getString(KEY_FIND_MY_URL, null).orEmpty()
            .ifBlank { BuildConfig.FIND_MY_PROXY_URL }
            .trimEnd('/')

    /**
     * DHL-Location-API: Basis-URL zur Laufzeit austauschbar (Sandbox/Prod),
     * da der öffentliche Endpunkt + OAuth2-Client-Credentials einen Vertrag
     * voraussetzen (siehe IMPLEMENTIERUNGS_INVENTUR.md §3).
     */
    val dhlApiUrl: String
        get() = prefs.getString(KEY_DHL_URL, null).orEmpty()
            .ifBlank { BuildConfig.DHL_API_URL }
            .trimEnd('/')

    /** Optionaler Bearer-Token (DHL OAuth2 Access-Token); leer = ohne Auth-Header. */
    val dhlApiToken: String
        get() = prefs.getString(KEY_DHL_TOKEN, null).orEmpty()
            .ifBlank { BuildConfig.DHL_API_TOKEN }

    /**
     * X-API-Key für schreibende Backend-Endpunkte (F-71). Leerer Prefs-Eintrag
     * fällt auf BuildConfig.SECUREGUARD_API_KEY zurück; beides leer = Header
     * entfällt (Pilot ohne API-Schutz).
     */
    val backendApiKey: String
        get() = prefs.getString(KEY_API_KEY, null).orEmpty()
            .ifBlank { BuildConfig.SECUREGUARD_API_KEY }
            .trim()

    // ---- Mutators (Settings-UI) ----

    fun update(
        mqttUrl: String? = null,
        mqttUser: String? = null,
        mqttPass: String? = null,
        websocketUrl: String? = null,
        mcpUrl: String? = null,
        backendUrl: String? = null,
        loraUrl: String? = null,
        yoloUrl: String? = null,
        ckanUrl: String? = null,
        findMyUrl: String? = null,
        dhlUrl: String? = null,
        dhlToken: String? = null,
        apiKey: String? = null
    ) {
        prefs.edit().apply {
            mqttUrl?.let { putString(KEY_MQTT_URL, it.trim()) }
            mqttUser?.let { putString(KEY_MQTT_USER, it.trim()) }
            mqttPass?.let { putString(KEY_MQTT_PASS, it) }
            websocketUrl?.let { putString(KEY_WS_URL, it.trim()) }
            mcpUrl?.let { putString(KEY_MCP_URL, it.trim()) }
            backendUrl?.let { putString(KEY_BACKEND_URL, it.trim()) }
            loraUrl?.let { putString(KEY_LORA_URL, it.trim()) }
            yoloUrl?.let { putString(KEY_YOLO_URL, it.trim()) }
            ckanUrl?.let { putString(KEY_CKAN_URL, it.trim()) }
            findMyUrl?.let { putString(KEY_FIND_MY_URL, it.trim()) }
            dhlUrl?.let { putString(KEY_DHL_URL, it.trim()) }
            dhlToken?.let { putString(KEY_DHL_TOKEN, it.trim()) }
            apiKey?.let { putString(KEY_API_KEY, it.trim()) }
        }.apply()
    }

    /** Snapshot für die Settings-UI (BuildConfig-Defaults sichtbar). */
    fun snapshot(): EndpointSnapshot = EndpointSnapshot(
        mqttBrokerUrl = mqttBrokerUrl,
        mqttUsername = mqttUsername,
        mqttPassword = mqttPassword,
        websocketUrl = websocketUrl,
        mcpServerUrl = mcpServerUrl,
        backendBaseUrl = backendBaseUrl,
        loraGatewayUrl = loraGatewayUrl,
        yoloServerUrl = yoloServerUrl,
        openDataApiUrl = openDataApiUrl.trimEnd('/'),
        findMyProxyUrl = findMyProxyUrl,
        dhlApiUrl = dhlApiUrl,
        dhlApiToken = dhlApiToken,
        backendApiKey = backendApiKey
    )

    companion object {
        private const val PREFS = "secureguard_endpoints"
        private const val KEY_MQTT_URL = "mqtt_url"
        private const val KEY_MQTT_USER = "mqtt_user"
        private const val KEY_MQTT_PASS = "mqtt_pass"
        private const val KEY_WS_URL = "ws_url"
        private const val KEY_MCP_URL = "mcp_url"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_LORA_URL = "lora_url"
        private const val KEY_YOLO_URL = "yolo_url"
        private const val KEY_CKAN_URL = "ckan_url"
        private const val KEY_FIND_MY_URL = "find_my_url"
        private const val KEY_API_KEY = "backend_api_key"
        private const val KEY_DHL_URL = "dhl_url"
        private const val KEY_DHL_TOKEN = "dhl_token"

        private const val DEFAULT_MQTT = "tcp://10.0.2.2:1883"
        private const val DEFAULT_CKAN = "https://demo.ckan.org/"

        /** `mqtt://host:port` → `tcp://host:port`; `mqtts://` → `ssl://`. */
        fun normalizeMqtt(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return t
            return when {
                t.startsWith("mqtts://", ignoreCase = true) ->
                    "ssl://" + t.removePrefix("mqtts://").removePrefix("MQTTS://")
                t.startsWith("mqtt://", ignoreCase = true) ->
                    "tcp://" + t.removePrefix("mqtt://").removePrefix("MQTT://")
                else -> t
            }
        }

        fun deriveHttpBase(wsOrHttp: String): String {
            if (wsOrHttp.isBlank()) return ""
            return wsOrHttp
                .replace("ws://", "http://")
                .replace("wss://", "https://")
                .removeSuffix("/ws")
                .trimEnd('/')
        }

        private fun ensureTrailingSlash(url: String): String {
            val t = url.trim()
            if (t.isEmpty()) return t
            return if (t.endsWith("/")) t else "$t/"
        }
    }
}

data class EndpointSnapshot(
    val mqttBrokerUrl: String = "",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val websocketUrl: String = "",
    val mcpServerUrl: String = "",
    val backendBaseUrl: String = "",
    val loraGatewayUrl: String = "",
    val yoloServerUrl: String = "",
    val openDataApiUrl: String = "",
    val findMyProxyUrl: String = "",
    val dhlApiUrl: String = "",
    val dhlApiToken: String = "",
    val backendApiKey: String = ""
)
