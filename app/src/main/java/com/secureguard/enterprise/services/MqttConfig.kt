package com.secureguard.enterprise.services

import com.secureguard.enterprise.BuildConfig

/**
 * Zentrale MQTT-Konfiguration (Broker, Topics, QoS).
 *
 * Der Broker kann über `MQTT_BROKER_URL` in gradle.properties /
 * local.properties gesetzt werden; ohne Eintrag gilt die lokale
 * Standard-URL (z. B. für den Docker-Compose-Broker aus `docker-compose.yml`).
 */
object MqttConfig {

    val BROKER_URL: String = BuildConfig.MQTT_BROKER_URL
        .ifBlank { "tcp://10.0.2.2:1883" } // 10.0.2.2 = Host-Rechner im Android-Emulator

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
