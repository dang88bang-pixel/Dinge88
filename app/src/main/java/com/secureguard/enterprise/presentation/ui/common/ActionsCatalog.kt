package com.secureguard.enterprise.presentation.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central action catalog for the Actions Center (§7/§8).
 *
 * Vehicle `DEVICE` actions are 1:1 wired to the eight existing Kotlin commands
 * ([ActionType]) and dispatched without any simulated response. Pure 3D scene
 * actions (SWEEP/FOCUS/GEOFENCE/FORCE) are `SCENE` entries — they only drive
 * the 3D presentation layer and MUST NOT trigger device commands (§8).
 */
enum class ActionRisk(val label: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    CRITICAL("CRITICAL")
}

enum class ActionCategory(val label: String) {
    CONNECTIVITY("Connectivity"),
    DIAGNOSTICS("Diagnostics"),
    SECURITY("Security"),
    DEVICE("Device"),
    LOCATION("Location"),
    AGENT("Agent"),
    SCENE("3D Scene")
}

enum class ActionKind {
    /** Real device command via existing services (MQTT/WebSocket/BLE + offline queue). */
    DEVICE,

    /** 3D presentation-only scene operation. Never touches device commands. */
    SCENE,

    /** Local agent/service operation (start/stop/cycle/queue-flush). */
    SERVICE
}

data class ActionDefinition(
    val key: String,
    val label: String,
    val category: ActionCategory,
    val risk: ActionRisk,
    val kind: ActionKind,
    val icon: ImageVector,
    val description: String,
    val requiresSelection: Boolean = true,
    val wireCommand: ActionType? = null
) {
    val isCritical: Boolean get() = risk == ActionRisk.CRITICAL || risk == ActionRisk.HIGH
    val isScene: Boolean get() = kind == ActionKind.SCENE
}

/**
 * Catalog definition. `SERVICE` entries are executed locally by
 * [com.secureguard.enterprise.services.AgentService]; `DEVICE` entries map 1:1
 * to the eight Kotlin device commands; `SCENE` entries are 3D-only.
 */
val ACTIONS_CATALOG: List<ActionDefinition> = listOf(
    // ---- Device (the existing eight Kotlin commands) ----
    ActionDefinition("ALARM", "Alarm", ActionCategory.SECURITY, ActionRisk.HIGH, ActionKind.DEVICE,
        Icons.Default.Warning, "Alarm am Asset auslösen.", wireCommand = ActionType.ALARM),
    ActionDefinition("LIGHT", "Licht signalisieren", ActionCategory.DEVICE, ActionRisk.LOW, ActionKind.DEVICE,
        Icons.Default.Lightbulb, "Blinklicht des Assets aktivieren.", wireCommand = ActionType.LIGHT),
    ActionDefinition("MOTOR_OFF", "Motor ausschalten", ActionCategory.SECURITY, ActionRisk.HIGH, ActionKind.DEVICE,
        Icons.Default.PowerSettingsNew, "Motor des Assets deaktivieren (Theft-Defense).", wireCommand = ActionType.MOTOR_OFF),
    ActionDefinition("BATTERY", "Batterie prüfen", ActionCategory.DIAGNOSTICS, ActionRisk.LOW, ActionKind.DEVICE,
        Icons.Default.BatteryAlert, "Batteriezustand des Assets abfragen.", wireCommand = ActionType.BATTERY),
    ActionDefinition("MESSAGE", "Nachricht senden", ActionCategory.CONNECTIVITY, ActionRisk.LOW, ActionKind.DEVICE,
        Icons.Default.Message, "Textnachricht an das Asset senden.", wireCommand = ActionType.MESSAGE),
    ActionDefinition("POSITION", "Position anfordern", ActionCategory.LOCATION, ActionRisk.MEDIUM, ActionKind.DEVICE,
        Icons.Default.LocationOn, "Aktuelle Position des Assets anfordern.", wireCommand = ActionType.POSITION),
    ActionDefinition("RESTART", "Neustart", ActionCategory.DEVICE, ActionRisk.CRITICAL, ActionKind.DEVICE,
        Icons.Default.Refresh, "Asset neu starten.", wireCommand = ActionType.RESTART),
    ActionDefinition("TELEMETRY", "Telemetrie abrufen", ActionCategory.DIAGNOSTICS, ActionRisk.LOW, ActionKind.DEVICE,
        Icons.Default.Storage, "Telemetriedaten des Assets abrufen.", wireCommand = ActionType.TELEMETRY),

    // ---- Agent / service operations (real local services) ----
    ActionDefinition("AGENT_START", "Agent starten", ActionCategory.AGENT, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.SmartToy, "Selbstlernenden Agent starten.", requiresSelection = false),
    ActionDefinition("AGENT_STOP", "Agent stoppen", ActionCategory.AGENT, ActionRisk.MEDIUM, ActionKind.SERVICE,
        Icons.Default.SmartToy, "Selbstlernenden Agent stoppen.", requiresSelection = false),
    ActionDefinition("AGENT_CYCLE", "Suchzyklus starten", ActionCategory.AGENT, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.Search, "Manuellen Agent-Zyklus ausführen.", requiresSelection = false),
    ActionDefinition("QUEUE_FLUSH", "Offline-Queue senden", ActionCategory.CONNECTIVITY, ActionRisk.MEDIUM, ActionKind.SERVICE,
        Icons.Default.Cloud, "Wartende Aktionen aus der Offline-Queue zustellen.", requiresSelection = false),
    ActionDefinition("MQTT_RECONNECT", "MQTT neu verbinden", ActionCategory.CONNECTIVITY, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.Wifi, "MQTT-Broker-Verbindung neu aufbauen.", requiresSelection = false),
    ActionDefinition("GPS_QUERY", "GPS-Status abfragen", ActionCategory.LOCATION, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.GpsFixed, "Lokalen GPS-Empfang prüfen.", requiresSelection = false),
    ActionDefinition("BLE_SCAN", "BLE-Scan", ActionCategory.CONNECTIVITY, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.Explore, "BLE-Kanal nach bekannten Assets scannen.", requiresSelection = false),
    ActionDefinition("DIAGNOSTICS", "Diagnosebericht", ActionCategory.DIAGNOSTICS, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.BugReport, "Systemzustand (Health) aufzeichnen.", requiresSelection = false),
    ActionDefinition("PERMISSIONS_AUDIT", "Berechtigungs-Audit", ActionCategory.SECURITY, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.PermDeviceInformation, "Runtime-Berechtigungen prüfen.", requiresSelection = false),
    ActionDefinition("DEVICE_INFO", "Geräteinfo", ActionCategory.DIAGNOSTICS, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.Memory, "Geräte- und Build-Zusammenfassung anzeigen.", requiresSelection = false),
    ActionDefinition("OPTICAL_SEARCH", "Optische Suche", ActionCategory.LOCATION, ActionRisk.MEDIUM, ActionKind.SERVICE,
        Icons.Default.CameraAlt, "Optischen Kanal (Kamera/YOLO) abfragen.", requiresSelection = false),
    ActionDefinition("LOCK_NOW", "App sperren", ActionCategory.SECURITY, ActionRisk.LOW, ActionKind.SERVICE,
        Icons.Default.Security, "App sofort sperren (PIN).", requiresSelection = false),

    // ---- 3D scene actions (presentation-only) ----
    ActionDefinition("SWEEP", "Sweep", ActionCategory.SCENE, ActionRisk.LOW, ActionKind.SCENE,
        Icons.Default.Explore, "3D-Scan-Sweep über die Karte.", requiresSelection = false),
    ActionDefinition("FOCUS", "Focus", ActionCategory.SCENE, ActionRisk.LOW, ActionKind.SCENE,
        Icons.Default.TravelExplore, "Kamera auf Auswahl fokussieren.", requiresSelection = false),
    ActionDefinition("GEOFENCE", "Geofence", ActionCategory.SCENE, ActionRisk.MEDIUM, ActionKind.SCENE,
        Icons.Default.LocationOn, "Geofence-Ring in der 3D-Szene setzen.", requiresSelection = false),
    ActionDefinition("FORCE", "Force", ActionCategory.SCENE, ActionRisk.HIGH, ActionKind.SCENE,
        Icons.Default.Warning, "Szenen-Effekt auf Asset-Knoten anwenden.", requiresSelection = false),
    ActionDefinition("ACTION", "Scene Action", ActionCategory.SCENE, ActionRisk.MEDIUM, ActionKind.SCENE,
        Icons.Default.MovieFilter, "Generische 3D-Szenenaktion ausführen.", requiresSelection = false)
)
