package com.secureguard.enterprise.services

/**
 * Configuration for the self-learning agent.
 *
 * @param interval poll interval in seconds
 * @param dynamicPriority let the agent re-prioritise channels based on history
 * @param learningMode recursive self-improvement — channels that recently
 *                     produced hits are queried first
 * @param offlineOnly never query external / internet channels
 * @param externalSources allow Apple/Google-style crowdsource lookups
 */
data class AgentSettings(
    val interval: Int = 30,
    val dynamicPriority: Boolean = true,
    val learningMode: Boolean = true,
    val offlineOnly: Boolean = true,
    val externalSources: Boolean = false
)

/** Live status of the agent, collected by the UI. */
data class AgentStatus(
    val running: Boolean = false,
    val startedAt: Long? = null,
    val lastRunAt: Long? = null,
    val nextRunAt: Long? = null,
    val cycle: Long = 0,
    val detectionsThisCycle: Int = 0,
    val settings: AgentSettings = AgentSettings()
) {
    val uptimeMillis: Long
        get() = startedAt?.let { System.currentTimeMillis() - it } ?: 0L
}

/** Result of a single agent cycle. */
data class AgentCycleResult(
    val assetsChecked: Int = 0,
    val detections: Int = 0,
    val channelHits: Map<String, Int> = emptyMap()
)

/**
 * Ergebnis einer automatisierten Registrierung mit temporärer E-Mail
 * (siehe [com.secureguard.enterprise.services.AgentService.autoRegisterExternalService]).
 */
data class RegistrationResult(
    val success: Boolean,
    val email: String = "",
    val otp: String = "",
    val inboxToken: String = "",
    val error: String? = null
)
