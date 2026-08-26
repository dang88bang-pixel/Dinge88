package com.secureguard.enterprise.services

import com.secureguard.enterprise.config.EndpointConfig

/**
 * MQTT-Themen und QoS-Konstanten.
 *
 * Die Broker-URL und Credentials kommen zur Laufzeit aus [EndpointConfig]
 * (Settings-UI → SharedPreferences → BuildConfig/local.properties).
 * [MqttService] liest die Werte bei jedem [MqttService.connect].
 */
object MqttConfig {

    const val CLIENT_ID_PREFIX = "secureguard"

    // Themen
    const val TOPIC_TELEMETRY = "secureguard/+/telemetry"
    const val TOPIC_ALERT = "secureguard/+/alert"
    const val TOPIC_COMMAND = "secureguard/+/command"
    const val TOPIC_STATUS = "secureguard/+/status"
    const val TOPIC_BROADCAST = "secureguard/broadcast"

    // QoS
    const val QOS_TELEMETRY = 1
    const val QOS_ALERT = 2
    const val QOS_COMMAND = 1

    const val KEEP_ALIVE_SECONDS = 60
    const val CONNECT_TIMEOUT_SECONDS = 10

    fun commandTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/command"
    fun telemetryTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/telemetry"
    fun alertTopic(assetMac: String) = "secureguard/${assetMac.uppercase()}/alert"
}

/**
 * Snapshot der MQTT-Verbindungsparameter (aus [EndpointConfig]).
 * Wird von [MqttService] beim Connect gelesen.
 */
data class MqttConnectionParams(
    val brokerUrl: String,
    val username: String = "",
    val password: String = ""
) {
    val hasAuth: Boolean get() = username.isNotBlank()
}
