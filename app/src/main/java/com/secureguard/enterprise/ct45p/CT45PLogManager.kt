package com.secureguard.enterprise.ct45p

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CT45P-lokale Log-Datei: protokolliert JEDEN Request/Response/Zyklus direkt
 * auf dem Gerät – für vollständige Anfragenverfolgbarkeit auf dem
 * Honeywell CT45P.
 *
 * Speicherort (in einem Dateimanager einsehbar):
 * `/storage/emulated/0/Android/data/com.secureguard.enterprise/files/SecureGuard/Logs/activity_log.txt`
 *
 * Der Benutzer sieht jede Anfrage (Typ, Endpoint, Parameter, Antwort,
 * Dauer, Erfolg/Fehler) – siehe [readAllLogs] und die CT45P-Statusleiste.
 */
@Singleton
class CT45PLogManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val LOG_DIR = "SecureGuard/Logs"
        const val LOG_FILENAME = "activity_log.txt"
        private const val TAG = "CT45PLogManager"
        const val SEPARATOR = "-".repeat(60)
    }

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    val logDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), LOG_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val logFile: File by lazy { File(logDir, LOG_FILENAME) }

    /**
     * Schreibt einen Request (Start) oder ein Ergebnis (Ende) im einheitlichen
     * Format in die Log-Datei. Thread-sicher (alle Agent-Kanäle rufen
     * asynchron herein).
     */
    @Synchronized
    fun logRequest(
        requestType: String,
        endpoint: String,
        parameters: Map<String, Any> = emptyMap(),
        response: String? = null,
        durationMs: Long = 0,
        success: Boolean = true,
        error: String? = null
    ) {
        val timestamp = timestampFormat.format(Date())
        val logEntry = buildString {
            appendLine("[$timestamp]")
            appendLine("  📡 REQUEST: $requestType")
            appendLine("  🔗 ENDPOINT: $endpoint")
            if (parameters.isNotEmpty()) {
                appendLine("  📦 PARAMS: $parameters")
            }
            if (response != null) {
                appendLine("  ✅ RESPONSE: ${response.take(200)}")
            }
            if (error != null) {
                appendLine("  ❌ ERROR: $error")
            }
            appendLine("  ⏱ DURATION: ${durationMs}ms")
            appendLine("  📊 SUCCESS: $success")
            appendLine(SEPARATOR)
        }

        try {
            FileWriter(logFile, true).use { writer ->
                writer.append(logEntry)
                writer.flush()
            }
        } catch (e: Exception) {
            // Fallback: Logcat
            Log.e(TAG, "Fehler beim Schreiben der Log-Datei: ${e.message}")
        }
    }

    /** Liest die komplette Log-Datei (leer, falls noch nicht vorhanden). */
    @Synchronized
    fun readAllLogs(): String = try {
        if (logFile.exists()) logFile.readText() else ""
    } catch (e: Exception) {
        Log.e(TAG, "Fehler beim Lesen der Log-Datei: ${e.message}")
        ""
    }

    /** Die letzten [n] Zeilen (für Vorschauen). */
    @Synchronized
    fun tailLines(n: Int = 500): List<String> = readAllLogs()
        .lines()
        .takeLast(n)
        .filter { it.isNotBlank() }

    /** Ein geparstes Log-Record (für Statusleiste, Statistik, Export). */
    data class ParsedEntry(
        val timestampMillis: Long,
        val requestType: String,
        val endpoint: String,
        val success: Boolean,
        val durationMs: Long,
        val error: String?
    )

    /** Parst die letzten [n] Einträge der Log-Datei. */
    fun parseTail(n: Int = 10): List<ParsedEntry> = allParsed().takeLast(n)

    /** Parst die gesamte Log-Datei in [ParsedEntry]-Objekte. */
    @Synchronized
    fun allParsed(): List<ParsedEntry> {
        val content = readAllLogs()
        if (content.isEmpty()) return emptyList()

        val entries = mutableListOf<ParsedEntry>()
        for (block in content.split(SEPARATOR)) {
            var timestampMillis = 0L
            var requestType = ""
            var endpoint = ""
            var success = false
            var durationMs = 0L
            var error: String? = null

            for (line in block.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                when {
                    trimmed.startsWith("[") -> {
                        val raw = trimmed.removePrefix("[").removeSuffix("]").trim()
                        timestampMillis =
                            runCatching { timestampFormat.parse(raw)?.time ?: 0L }.getOrDefault(0L)
                    }
                    trimmed.contains("REQUEST:") -> requestType = trimmed.substringAfter("REQUEST:").trim()
                    trimmed.contains("ENDPOINT:") -> endpoint = trimmed.substringAfter("ENDPOINT:").trim()
                    trimmed.contains("SUCCESS:") -> success = trimmed.substringAfter("SUCCESS:").trim() == "true"
                    trimmed.contains("DURATION:") ->
                        durationMs = trimmed.substringAfter("DURATION:").trim()
                            .removeSuffix("ms").toLongOrNull() ?: 0L
                    trimmed.contains("ERROR:") -> error = trimmed.substringAfter("ERROR:").trim()
                }
            }

            if (requestType.isNotEmpty()) {
                entries.add(
                    ParsedEntry(
                        timestampMillis = timestampMillis,
                        requestType = requestType,
                        endpoint = endpoint,
                        success = success,
                        durationMs = durationMs,
                        error = error
                    )
                )
            }
        }
        return entries
    }
}
