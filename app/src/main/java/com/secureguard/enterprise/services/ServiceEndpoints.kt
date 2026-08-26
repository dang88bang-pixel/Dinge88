package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.BuildConfig

/**
 * Laufzeit-Endpunkt-Konfiguration (Settings-Overrides).
 *
 * Die Werte können im Setup/Build über gradle.properties/local.properties
 * gesetzt werden (BuildConfig) – und zur Laufzeit in den Einstellungen
 * überschrieben werden (SharedPreferences). Ohne Override gelten die
 * BuildConfig-Angaben bzw. sinnvolle lokale Defaults:
 *
 *   - MQTT-Broker:  tcp://10.0.2.2:1883 (Android-Emulator → Host)
 *   - Backend/WS:   ws://10.0.2.2:8000/ws
 *   - MCP:          http://10.0.2.2:8000
 */
object ServiceEndpoints {

    const val PREFS_NAME = "secureguard_settings"
    const val KEY_MQTT = "endpoint_mqtt_url"
    const val KEY_WS = "endpoint_ws_url"
    const val KEY_MCP = "endpoint_mcp_url"
    const val KEY_MQTT_USER = "endpoint_mqtt_username"
    const val KEY_MQTT_PASS = "endpoint_mqtt_password"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun pref(context: Context, key: String): String? =
        prefs(context).getString(key, null)?.takeIf { it.isNotBlank() }

    /** MQTT-Broker-URL (z. B. tcp://192.168.1.100:1883). */
    fun mqttUrl(context: Context): String =
        pref(context, KEY_MQTT)
            ?: BuildConfig.MQTT_BROKER_URL.ifBlank { "tcp://10.0.2.2:1883" }

    /** Backend-WebSocket-URL (z. B. ws://192.168.1.100:8000/ws). */
    fun webSocketUrl(context: Context): String =
        pref(context, KEY_WS)
            ?: BuildConfig.WEBSOCKET_URL.ifBlank { "ws://10.0.2.2:8000/ws" }

    /** MCP-Server-URL (Temp-Mail/OTP; gleiche Backend-Instanz). */
    fun mcpUrl(context: Context): String =
        pref(context, KEY_MCP)
            ?: BuildConfig.MCP_SERVER_URL.ifBlank { "http://10.0.2.2:8000" }

    /** MQTT-Benutzername (Broker-Auth, optional). */
    fun mqttUsername(context: Context): String =
        pref(context, KEY_MQTT_USER)
            ?: BuildConfig.MQTT_USERNAME

    /** MQTT-Passwort (Broker-Auth, optional). */
    fun mqttPassword(context: Context): String =
        pref(context, KEY_MQTT_PASS)
            ?: BuildConfig.MQTT_PASSWORD

    /** HTTP-Basis-URL des Backends, abgeleitet aus der WS-URL. */
    fun backendHttpUrl(context: Context): String {
        val ws = webSocketUrl(context)
        return ws.replace("ws://", "http://")
            .replace("wss://", "https://")
            .removeSuffix("/ws")
            .trimEnd('/')
    }

    /** Speichert Laufzeit-Overrides (leerer String entfernt den Override). */
    fun save(
        context: Context,
        mqtt: String?,
        ws: String?,
        mcp: String?,
        mqttUser: String? = null,
        mqttPass: String? = null
    ) {
        prefs(context).edit()
            .apply {
                if (mqtt.isNullOrBlank()) remove(KEY_MQTT) else putString(KEY_MQTT, mqtt.trim())
                if (ws.isNullOrBlank()) remove(KEY_WS) else putString(KEY_WS, ws.trim())
                if (mcp.isNullOrBlank()) remove(KEY_MCP) else putString(KEY_MCP, mcp.trim())
                if (mqttUser.isNullOrBlank()) remove(KEY_MQTT_USER) else putString(KEY_MQTT_USER, mqttUser.trim())
                if (mqttPass.isNullOrBlank()) remove(KEY_MQTT_PASS) else putString(KEY_MQTT_PASS, mqttPass)
            }
            .apply()
    }
}
