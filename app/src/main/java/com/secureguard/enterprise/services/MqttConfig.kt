package com.secureguard.enterprise.services

import com.secureguard.enterprise.BuildConfig

/**
 * Zentrale MQTT-Konfiguration (Broker, Topics, QoS).
 *
 * Der Broker wird ausschließlich über `MQTT_BROKER_URL` in gradle.properties /
 * local.properties gesetzt. Ohne Konfiguration wird keine Verbindung
 * aufgebaut; ein stiller Fallback auf einen Demo-Broker ist bewusst entfernt.
 */
object MqttConfig {

    /** Leer, wenn nicht konfiguriert; die Service-Logik verbindet dann nicht. */
    val BROKER_URL: String = BuildConfig.MQTT_BROKER_URL.trim()

    val USERNAME: String = BuildConfig.MQTT_USERNAME.trim()
    val PASSWORD: String = BuildConfig.MQTT_PASSWORD.trim()
    val USE_TLS: Boolean = BuildConfig.MQTT_TLS

    const val CLIENT_ID_PREFIX = "secureguard"

    // Themen
    const val TOPIC_TELEMETRY = "secureguard/+/telemetry"
    const val TOPIC_ALERT = "secureguard/+/alert"
    const val TOPIC_COMMAND = "secureguard/+/command"
    const val TOPIC_STATUS = "secureguard/+/status"
    const val TOPIC_BROADCAST = "secureguard/broadcast"
    const val TOPIC_SEARCH_REQUEST = "secureguard/search/request"
    const val TOPIC_SEARCH_RESPONSE = "secureguard/+/search/response"
    const val TOPIC_SEARCH_RESPONSE_ALT = "secureguard/+/search_response"

    // QoS
    const val QOS_TELEMETRY = 1
    const val QOS_ALERT = 2
    const val QOS_COMMAND = 1
    const val QOS_SEARCH = 1

    const val KEEP_ALIVE_SECONDS = 60
    const val CONNECT_TIMEOUT_SECONDS = 10

    fun commandTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/command"
    fun telemetryTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/telemetry"
    fun alertTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/alert"
    fun searchResponseTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/search/response"
}
