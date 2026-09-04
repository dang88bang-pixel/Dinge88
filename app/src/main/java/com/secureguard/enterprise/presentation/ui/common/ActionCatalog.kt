package com.secureguard.enterprise.presentation.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** Fachliche Gruppierung der Aktionen im Aktions-Katalog. */
enum class ActionCategory(val label: String, val icon: ImageVector) {
    SIGNAL("Signalisieren", Icons.Default.Campaign),
    CONTROL("Steuern", Icons.Default.Tune),
    QUERY("Abfragen", Icons.Default.QueryStats),
    MAINTENANCE("Wartung", Icons.Default.Build)
}

/**
 * Risikostufe einer Aktion. [CRITICAL] erzwingt einen Bestätigungsdialog,
 * bevor der Befehl das Gerät erreicht.
 */
enum class ActionRisk(val label: String) {
    SAFE("unkritisch"),
    CAUTION("mit Bedacht"),
    CRITICAL("kritisch")
}

/**
 * Vollständige Beschreibung einer ausführbaren Aktion: Titel, Erklärung,
 * Icon, Kategorie, Risiko und Zustellverhalten. Damit weiß der Anwender vor
 * dem Tippen, was passiert – statt nur ein Emoji auf einem Button zu sehen.
 */
data class ActionSpec(
    val type: ActionType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val category: ActionCategory,
    val risk: ActionRisk = ActionRisk.SAFE,
    /** Aktion ergibt nur online Sinn (sonst nur Warteschlange). */
    val requiresOnline: Boolean = false,
    /** Darf bei Verbindungsverlust in die Offline-Queue. */
    val queueable: Boolean = true,
    /** Aktion nimmt einen Freitext (z. B. Displaynachricht) entgegen. */
    val acceptsNote: Boolean = false,
    val confirmTitle: String? = null,
    val confirmMessage: String? = null
)

/** Statischer Katalog aller vom Agenten unterstützten Befehle. */
object ActionCatalog {

    val all: List<ActionSpec> = listOf(
        ActionSpec(
            type = ActionType.ALARM,
            title = "Alarm auslösen",
            description = "Akustischer Alarm am Asset – für Wiederauffinden vor Ort.",
            icon = Icons.Default.NotificationsActive,
            category = ActionCategory.SIGNAL,
            risk = ActionRisk.CAUTION
        ),
        ActionSpec(
            type = ActionType.LIGHT,
            title = "Blinken",
            description = "LED-Signal aktivieren – leise Alternative zum Alarm.",
            icon = Icons.Default.Highlight,
            category = ActionCategory.SIGNAL
        ),
        ActionSpec(
            type = ActionType.MESSAGE,
            title = "Nachricht senden",
            description = "Freitext an das Display / den Empfänger des Assets.",
            icon = Icons.Default.Sms,
            category = ActionCategory.SIGNAL,
            acceptsNote = true
        ),
        ActionSpec(
            type = ActionType.MOTOR_OFF,
            title = "Motor abschalten",
            description = "Antrieb sperren. Nur im Stillstand verwenden!",
            icon = Icons.Default.PowerSettingsNew,
            category = ActionCategory.CONTROL,
            risk = ActionRisk.CRITICAL,
            requiresOnline = true,
            queueable = false,
            confirmTitle = "Motor wirklich abschalten?",
            confirmMessage = "Der Antrieb wird sofort gesperrt. Führe diese Aktion nur " +
                "aus, wenn das Fahrzeug steht – sonst besteht Unfallgefahr."
        ),
        ActionSpec(
            type = ActionType.RESTART,
            title = "Gerät neu starten",
            description = "Controller neu starten. Kurzzeitiger Verbindungsverlust.",
            icon = Icons.Default.RestartAlt,
            category = ActionCategory.CONTROL,
            risk = ActionRisk.CRITICAL,
            requiresOnline = true,
            queueable = false,
            confirmTitle = "Neustart auslösen?",
            confirmMessage = "Das Asset ist für ca. 10–30 Sekunden nicht erreichbar " +
                "und meldet sich danach neu am Agenten an."
        ),
        ActionSpec(
            type = ActionType.POSITION,
            title = "Position anfordern",
            description = "Sofortige Standortmeldung über den schnellsten Kanal.",
            icon = Icons.Default.MyLocation,
            category = ActionCategory.QUERY
        ),
        ActionSpec(
            type = ActionType.BATTERY,
            title = "Akkustand abfragen",
            description = "Ladezustand und Spannung des Assets auslesen.",
            icon = Icons.Default.BatteryChargingFull,
            category = ActionCategory.QUERY
        ),
        ActionSpec(
            type = ActionType.TELEMETRY,
            title = "Telemetrie abrufen",
            description = "Vollständigen Sensor-Datensatz anfordern und speichern.",
            icon = Icons.Default.Insights,
            category = ActionCategory.QUERY
        )
    )

    private val byType: Map<ActionType, ActionSpec> = all.associateBy { it.type }

    fun of(type: ActionType): ActionSpec = byType.getValue(type)

    fun byCategory(category: ActionCategory): List<ActionSpec> =
        all.filter { it.category == category }

    /** Kategorien in Anzeigereihenfolge, leere werden ausgelassen. */
    val categories: List<ActionCategory> =
        ActionCategory.entries.filter { cat -> all.any { it.category == cat } }
}
