package com.secureguard.enterprise.presentation.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.presentation.theme.AccentAmber
import com.secureguard.enterprise.presentation.theme.AccentCyan
import com.secureguard.enterprise.presentation.theme.AccentGreen
import com.secureguard.enterprise.presentation.theme.AccentRed
import com.secureguard.enterprise.presentation.theme.StatusMaintenance
import com.secureguard.enterprise.presentation.theme.StatusSearching

/**
 * Zentrale Design-Tokens des SecureGuard-Design-Systems.
 *
 * Alle Screens verwenden diese Werte, damit Abstände, Radien und
 * Statusfarben app-weit identisch sind ("ein Look, keine Insellösungen").
 */
object Sg {

    /** 4-dp-Raster – alle Abstände sind Vielfache davon. */
    object Space {
        val xxs = 2.dp
        val xs = 4.dp
        val sm = 8.dp
        val md = 12.dp
        val lg = 16.dp
        val xl = 20.dp
        val xxl = 24.dp
        val xxxl = 32.dp
    }

    object Radius {
        val sm = 8.dp
        val md = 14.dp
        val lg = 20.dp
        val xl = 28.dp
        val pill = 100.dp
    }

    object Size {
        val icon = 20.dp
        val iconLarge = 28.dp
        val avatar = 44.dp
        val touchTarget = 48.dp
        val sparklineHeight = 44.dp
        val ring = 92.dp
    }
}

/** Farbe eines Asset-Status – wird für Punkte, Rahmen und Chips genutzt. */
fun statusColor(status: AssetStatus): Color = when (status) {
    AssetStatus.ONLINE -> AccentGreen
    AssetStatus.OFFLINE -> AccentRed
    AssetStatus.MAINTENANCE -> StatusMaintenance
    AssetStatus.SEARCHING -> StatusSearching
    AssetStatus.UNKNOWN -> Color(0xFF78909C)
}

/** Kurzlabel eines Asset-Status (deutsch, für Chips/Pills). */
fun statusLabel(status: AssetStatus): String = when (status) {
    AssetStatus.ONLINE -> "Online"
    AssetStatus.OFFLINE -> "Offline"
    AssetStatus.MAINTENANCE -> "Wartung"
    AssetStatus.SEARCHING -> "Suche"
    AssetStatus.UNKNOWN -> "Unbekannt"
}

/** Farbe einer Alarm-Schwere. */
fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.CRITICAL -> AccentRed
    AlertSeverity.WARNING -> AccentAmber
    AlertSeverity.INFO -> AccentCyan
}

fun severityLabel(severity: AlertSeverity): String = when (severity) {
    AlertSeverity.CRITICAL -> "Kritisch"
    AlertSeverity.WARNING -> "Warnung"
    AlertSeverity.INFO -> "Info"
}

/** Eigene Signalfarbe je Detection-Kanal – macht die Kanal-Matrix lesbar. */
fun sourceColor(source: DetectionSource): Color = when (source) {
    DetectionSource.BLE -> Color(0xFF448AFF)
    DetectionSource.WIFI -> Color(0xFF00D4FF)
    DetectionSource.LORA -> Color(0xFF9C6BFF)
    DetectionSource.TELEMETRY -> Color(0xFF00E676)
    DetectionSource.OPTICAL -> Color(0xFFFF7043)
    DetectionSource.URBAN -> Color(0xFFFFC400)
    DetectionSource.CROWD -> Color(0xFF26C6DA)
    DetectionSource.SATELLITE -> Color(0xFFEC407A)
    DetectionSource.API -> Color(0xFF7E57C2)
    DetectionSource.MQTT -> Color(0xFF66BB6A)
    DetectionSource.WEBSOCKET -> Color(0xFF29B6F6)
    DetectionSource.NFC -> Color(0xFFFFA726)
    DetectionSource.UNKNOWN -> Color(0xFF78909C)
}

/** Icon je Detection-Kanal. */
fun sourceIcon(source: DetectionSource): ImageVector = when (source) {
    DetectionSource.BLE -> Icons.Default.Bluetooth
    DetectionSource.WIFI -> Icons.Default.Wifi
    DetectionSource.LORA -> Icons.Default.Radar
    DetectionSource.TELEMETRY -> Icons.Default.Sensors
    DetectionSource.OPTICAL -> Icons.Default.Videocam
    DetectionSource.URBAN -> Icons.Default.LocationCity
    DetectionSource.CROWD -> Icons.Default.Groups
    DetectionSource.SATELLITE -> Icons.Default.SatelliteAlt
    DetectionSource.API -> Icons.Default.Language
    DetectionSource.MQTT -> Icons.Default.Hub
    DetectionSource.WEBSOCKET -> Icons.Default.CloudQueue
    DetectionSource.NFC -> Icons.Default.Nfc
    DetectionSource.UNKNOWN -> Icons.Default.HelpOutline
}

fun sourceLabel(source: DetectionSource): String = when (source) {
    DetectionSource.BLE -> "BLE"
    DetectionSource.WIFI -> "WiFi"
    DetectionSource.LORA -> "LoRa"
    DetectionSource.TELEMETRY -> "Telemetrie"
    DetectionSource.OPTICAL -> "Optisch"
    DetectionSource.URBAN -> "Urban"
    DetectionSource.CROWD -> "Crowd"
    DetectionSource.SATELLITE -> "Satellit"
    DetectionSource.API -> "API"
    DetectionSource.MQTT -> "MQTT"
    DetectionSource.WEBSOCKET -> "WebSocket"
    DetectionSource.NFC -> "NFC"
    DetectionSource.UNKNOWN -> "Unbekannt"
}

/** Ampelfarbe für einen RSSI-Wert (dBm). */
fun rssiColor(rssi: Int): Color = when {
    rssi == 0 -> Color(0xFF78909C)
    rssi > -60 -> AccentGreen
    rssi > -75 -> AccentAmber
    else -> AccentRed
}

/** 0..4 Balken für einen RSSI-Wert. */
fun rssiBars(rssi: Int): Int = when {
    rssi == 0 -> 0
    rssi > -55 -> 4
    rssi > -68 -> 3
    rssi > -80 -> 2
    else -> 1
}

/** Ampelfarbe für einen Ladestand in Prozent. */
fun batteryColor(percent: Int): Color = when {
    percent >= 60 -> AccentGreen
    percent >= 25 -> AccentAmber
    else -> AccentRed
}

/** "vor 3 Min." statt Rohzeitstempel – überall gleich formatiert. */
fun relativeTime(timestampMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (timestampMillis == null || timestampMillis <= 0L) return "nie"
    val diff = now - timestampMillis
    if (diff < 0) return "gerade eben"
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 45 -> "gerade eben"
        minutes < 60 -> "vor $minutes Min."
        hours < 24 -> "vor $hours Std."
        days < 30 -> "vor $days Tg."
        else -> "vor ${days / 30} Mon."
    }
}

/** Kompakte Dauer, z. B. "2h 14m" – für Agent-Laufzeiten. */
fun compactDuration(millis: Long): String {
    if (millis <= 0L) return "0s"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "${seconds}s"
        minutes < 60 -> "${minutes}m ${seconds % 60}s"
        hours < 24 -> "${hours}h ${minutes % 60}m"
        else -> "${days}d ${hours % 24}h"
    }
}
