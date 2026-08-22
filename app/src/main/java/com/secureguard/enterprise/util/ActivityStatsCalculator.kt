package com.secureguard.enterprise.util

import com.secureguard.enterprise.ct45p.CT45PLogManager
import java.util.Date

/**
 * Berechnet die 24h-Statistik für die CT45P-Statusleiste und den
 * Aktivitätsverlauf: erfolgreich/fehlgeschlagene Anfragen, Treffer
 * pro Quelle und durchschnittliche Antwortzeit.
 *
 * Grundlage ist die on-device Log-Datei des [CT45PLogManager] – exakt die
 * Daten, die der CT45P-Benutzer einsehen kann.
 */
object ActivityStatsCalculator {

    data class ActivityStats(
        val successCount: Int = 0,
        val errorCount: Int = 0,
        val bySource: Map<String, Int> = emptyMap(),
        val averageDurationMs: Long = 0,
        val windowHours: Int = 24
    )

    /** Menschenlesbare Quellen-Bezeichnung für einen Request-Typ. */
    fun sourceKey(requestType: String): String = when {
        requestType.contains("LORA", ignoreCase = true) -> "LoRa"
        requestType.contains("BLE", ignoreCase = true) -> "BLE"
        requestType.contains("WIFI", ignoreCase = true) -> "WiFi"
        requestType.contains("GPS", ignoreCase = true) ||
            requestType.contains("SATELLITE", ignoreCase = true) -> "GPS"
        requestType.contains("OPTICAL", ignoreCase = true) -> "Optik"
        requestType.contains("CROWD", ignoreCase = true) -> "Crowd"
        requestType.contains("ACTION", ignoreCase = true) -> "Aktion"
        requestType.equals("ERROR", ignoreCase = true) -> "Fehler"
        else -> "Urban"
    }

    /**
     * @param logManager liefert die on-device Log-Einträge
     * @param since Beginn des Zeitfensters (Default: vor 24 h)
     */
    fun calculate(
        logManager: CT45PLogManager,
        since: Date = Date(System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
    ): ActivityStats {
        val cutoff = since.time
        val entries = logManager.allParsed()
            .filter { it.timestampMillis == 0L || it.timestampMillis >= cutoff }

        val success = entries.count { it.success }
        val errors = entries.count { !it.success }
        val bySource = entries
            .filter { it.success && !it.requestType.equals("ERROR", ignoreCase = true) }
            .groupingBy { sourceKey(it.requestType) }
            .eachCount()
        val durations = entries.map { it.durationMs }.filter { it > 0 }
        val avg = durations.average().toLong()

        return ActivityStats(
            successCount = success,
            errorCount = errors,
            bySource = bySource,
            averageDurationMs = avg
        )
    }
}
