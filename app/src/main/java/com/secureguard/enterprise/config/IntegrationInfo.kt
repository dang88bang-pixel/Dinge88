package com.secureguard.enterprise.config

/**
 * Zustand einer Anbindung/Abhängigkeit.
 *
 * Wird im Einstellungsmenü unter „🧩 Anbindungen & Abhängigkeiten" gerendert –
 * eine Zeile pro Integration (App-seitig **und** serverseitig).
 */
enum class IntegrationState {
    /** Live geprüft und erreichbar. */
    CONNECTED,

    /** Konfiguriert, aber nicht (oder noch nicht) geprüft/verbunden. */
    CONFIGURED,

    /** Bewusst deaktiviert bzw. nicht konfiguriert. */
    DISABLED,

    /** Konfiguriert, aber nicht erreichbar. */
    MISSING,

    /** Zustand unbekannt (z. B. ohne Backend). */
    UNKNOWN
}

/**
 * Eine Zeile in der Abhängigkeitsliste.
 *
 * @param origin `app` = Konfiguration liegt auf dem Gerät (Settings/local.properties),
 *               `server` = kommt vom Backend (`GET /api/system/dependencies`).
 */
data class IntegrationInfo(
    val id: String,
    val name: String,
    val kind: String,
    val target: String,
    val state: IntegrationState,
    val source: String,
    val detail: String = "",
    val origin: String = ORIGIN_APP
) {
    val icon: String
        get() = when (state) {
            IntegrationState.CONNECTED -> "🟢"
            IntegrationState.CONFIGURED -> "🟡"
            IntegrationState.DISABLED -> "⚪"
            IntegrationState.MISSING -> "🔴"
            IntegrationState.UNKNOWN -> "❔"
        }

    val stateLabel: String
        get() = when (state) {
            IntegrationState.CONNECTED -> "verbunden"
            IntegrationState.CONFIGURED -> "konfiguriert"
            IntegrationState.DISABLED -> "aus"
            IntegrationState.MISSING -> "nicht erreichbar"
            IntegrationState.UNKNOWN -> "unbekannt"
        }

    companion object {
        const val ORIGIN_APP = "app"
        const val ORIGIN_SERVER = "server"

        /**
         * Mappt die Server-Antwort (`configured`/`reachable`) auf einen Zustand.
         * `reachable = null` heißt „nicht geprüft" (z. B. Webhook-Fallback).
         */
        fun stateFromProbe(configured: Boolean, reachable: Boolean?): IntegrationState = when {
            !configured -> IntegrationState.DISABLED
            reachable == true -> IntegrationState.CONNECTED
            reachable == false -> IntegrationState.MISSING
            else -> IntegrationState.CONFIGURED
        }

        /** Secrets werden nie angezeigt – nur ob sie gesetzt sind. */
        fun secretState(value: String?): String =
            if (value.isNullOrBlank()) "nicht gesetzt" else "gesetzt"
    }
}
