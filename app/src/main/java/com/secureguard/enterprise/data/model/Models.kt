package com.secureguard.enterprise.data.model

/**
 * Result of a multi-channel asset search performed by the agent or by the
 * asset detail screen. [accuracy] mirrors the best RSSI (lower is better).
 *
 * [providerErrors] unterscheidet echte Provider-Fehler (Netzfehler/HTTP-Fehler)
 * von „keine Treffer" (Found=false, leere providerErrors).
 */
data class SearchResult(
    val found: Boolean,
    val detection: Detection? = null,
    val accuracy: Int = Int.MAX_VALUE,
    val providerErrors: Map<DetectionSource, String> = emptyMap()
) {
    companion object {
        val NotFound = SearchResult(found = false)
    }
}
