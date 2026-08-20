package com.secureguard.enterprise.agent

/**
 * Konfiguration eines einzelnen API-Knotens. Wird vom [ApiNodeManager]
 * genutzt; die Standard-Werte liegen in [DefaultNodeConfigs].
 */
data class NodeConfig(
    val enabled: Boolean = true,
    val timeoutMs: Long = 10_000,
    val maxRetries: Int = 3,
    val priority: Int = 50,
    val rateLimitPerMinute: Int = 10,
    val fallbackNodes: List<String> = emptyList(),
    val requiresAuth: Boolean = false,
    val apiKey: String? = null
)

/** Standard-Konfigurationen für alle Abfrageknoten. */
object DefaultNodeConfigs {

    val WIGLE = NodeConfig(
        priority = 80,
        rateLimitPerMinute = 10,
        timeoutMs = 15_000
    )

    val MACLOOKUP = NodeConfig(
        priority = 60,
        rateLimitPerMinute = 30,
        timeoutMs = 5_000
    )

    val OPEN_CHARGE_MAP = NodeConfig(
        priority = 40,
        rateLimitPerMinute = 5,
        timeoutMs = 10_000
    )

    val DHL = NodeConfig(
        priority = 50,
        rateLimitPerMinute = 10,
        timeoutMs = 8_000
    )

    val CKAN = NodeConfig(
        priority = 30,
        rateLimitPerMinute = 20,
        timeoutMs = 10_000
    )

    val GOOGLE_GEO = NodeConfig(
        priority = 90,
        rateLimitPerMinute = 50,
        timeoutMs = 5_000,
        requiresAuth = true
    )

    val NETATMO = NodeConfig(
        priority = 20,
        rateLimitPerMinute = 10,
        timeoutMs = 8_000,
        requiresAuth = true
    )

    val HELIUM = NodeConfig(
        priority = 70,
        rateLimitPerMinute = 15,
        timeoutMs = 10_000,
        requiresAuth = true
    )

    val MQTT = NodeConfig(
        priority = 85,
        rateLimitPerMinute = 100,
        timeoutMs = 3_000
    )

    val WEBSOCKET = NodeConfig(
        priority = 75,
        rateLimitPerMinute = 100,
        timeoutMs = 5_000
    )

    val TEMPMAIL = NodeConfig(
        priority = 25,
        rateLimitPerMinute = 5,
        timeoutMs = 45_000
    )
}
