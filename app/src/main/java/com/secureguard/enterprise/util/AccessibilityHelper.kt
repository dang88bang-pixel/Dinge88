package com.secureguard.enterprise.util

import android.content.Context
import android.view.accessibility.AccessibilityManager

/**
 * Barrierefreiheit (TalkBack): Hilfsfunktionen für Screenreader-freundliche
 * Inhaltsbeschreibungen und Status-Ausgaben.
 */
object AccessibilityHelper {

    fun isTalkBackEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isEnabled == true && am.isTouchExplorationEnabled
    }

    fun contentDescriptionForStatus(status: String): String = when (status) {
        "ONLINE" -> "Online"
        "OFFLINE" -> "Offline"
        "MAINTENANCE" -> "Wartung erforderlich"
        "SEARCHING" -> "Wird gesucht"
        else -> "Unbekannter Status"
    }

    fun contentDescriptionForSource(source: String): String = when (source) {
        "BLE" -> "Bluetooth"
        "WIFI" -> "WLAN"
        "LORA" -> "LoRa"
        "TELEMETRY" -> "Telemetrie"
        "OPTICAL" -> "Optische Erkennung"
        "URBAN" -> "Urbane Infrastruktur"
        "CROWD" -> "Crowdsourcing-Netzwerk"
        "SATELLITE" -> "Satellit"
        "API" -> "Externe API"
        "MQTT" -> "MQTT"
        "WEBSOCKET" -> "WebSocket"
        "NFC" -> "NFC"
        else -> "Unbekannte Quelle"
    }
}
