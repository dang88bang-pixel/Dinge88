package com.secureguard.enterprise.ct45p

import android.content.Context
import android.os.Build
import com.secureguard.enterprise.util.ActivityStatsCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CT45P-Log-Export (für Analyse): erstellt eine Kopie des Aktivitätslogs
 * mit Header und 24h-Statistik im separaten Export-Ordner:
 * `SecureGuard/Export/log_export_<timestamp>.txt`
 */
@Singleton
class CT45PLogExport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logManager: CT45PLogManager
) {

    private val exportDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), "SecureGuard/Export")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun exportLogs(): File {
        val stats = ActivityStatsCalculator.calculate(logManager)
        val sourceText = stats.bySource.entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key} (${it.value})" }

        val exportFile = File(exportDir, "log_export_${System.currentTimeMillis()}.txt")
        exportFile.writeText(buildString {
            appendLine("=".repeat(80))
            appendLine("SECUREGUARD PRO – AKTIVITÄTSLOG")
            appendLine("Export erstellt: ${Date()}")
            appendLine("Gerät: CT45P (${Build.MANUFACTURER} ${Build.MODEL})")
            appendLine("=".repeat(80))
            appendLine()
            append(logManager.readAllLogs())
            appendLine()
            appendLine("=".repeat(80))
            appendLine("STATISTIK (Letzte ${stats.windowHours}h)")
            appendLine("=".repeat(80))
            appendLine("✅ Erfolgreich: ${stats.successCount}")
            appendLine("❌ Fehler: ${stats.errorCount}")
            appendLine("📡 Quellen: ${sourceText.ifEmpty { "–" }}")
            appendLine("⏱ Durchschnittliche Antwortzeit: " +
                String.format(Locale.GERMANY, "%.1f s", stats.averageDurationMs / 1000.0))
            appendLine("=".repeat(80))
        })
        return exportFile
    }
}
