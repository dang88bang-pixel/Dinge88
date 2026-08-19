package com.secureguard.enterprise.data.model

/**
 * Konfiguration des selbstlernenden Agents.
 */
data class AgentSettings(
    var interval: Int = 30,
    var dynamicPriority: Boolean = true,
    var learningMode: Boolean = true,
    var offlineOnly: Boolean = true,
    var externalSources: Boolean = false
)
