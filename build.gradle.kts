// Top-level build file where you can add configuration options common to all sub-projects/modules.
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LogMessageData
import org.gradle.api.logging.LogRequest
import org.gradle.api.logging.LoggingListener
import org.gradle.api.logging.LoggingService

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

// =============================================================================
// CI-Fehler-Erfassung (aktiv NUR in GitHub Actions)
// =============================================================================
// Die Sandbox kann die Actions-Run-Logs nicht abrufen (Firewall). Daher
// schreibt der Build bei einem Fehler die Diagnose (Exception-Kette +
// letzte Konsolenzeilen) über die Contents-API automatisch in
// `ci-error.txt` auf den aktuellen Branch – dort lesbar ohne Log-Zugriff.
// Lokal (ohne GITHUB_TOKEN) bleibt der Hook wirkungslos.
// =============================================================================

val ciLogBuffer = java.util.ArrayDeque<String>()

gradle.services.get(LoggingService::class.java).addLogListener(object : LoggingListener {
    override fun requestStarted(request: LogRequest?) {
        // nicht benötigt
    }

    override fun messageLogged(message: LogMessageData) {
        val level = message.level
        if (level == LogLevel.LIFECYCLE || level == LogLevel.ERROR ||
            level == LogLevel.WARNING || level == LogLevel.INFO
        ) {
            val line = message.message?.toString() ?: return
            ciLogBuffer.addLast(line)
            while (ciLogBuffer.size > 500) {
                ciLogBuffer.removeFirst()
            }
        }
    }
})

gradle.buildFinished { result ->
    if (result.failure == null) return@buildFinished
    val token = System.getenv("GITHUB_TOKEN") ?: return@buildFinished
    val repo = System.getenv("GITHUB_REPOSITORY") ?: return@buildFinished
    val ref = System.getenv("GITHUB_REF") ?: return@buildFinished
    val branch = ref.removePrefix("refs/heads/").removePrefix("refs/tags/")
    try {
        val sb = StringBuilder()
        sb.appendLine("GITHUB_RUN_ID=${System.getenv("GITHUB_RUN_ID") ?: "?"}")
        sb.appendLine("Zeit: ${System.currentTimeMillis()}")
        sb.appendLine()
        var e: Throwable? = result.failure.exception
        var depth = 0
        while (e != null && depth < 6) {
            sb.appendLine("[Fehler-Kette $depth] ${e.javaClass.name}: ${e.message}")
            e.stackTrace?.take(15)?.forEach { s -> sb.appendLine("    at $s") }
            e = e.cause
            depth++
        }
        sb.appendLine()
        sb.appendLine("===== LETZTE KONSOLENZEILEN (Build-Log) =====")
        ciLogBuffer.forEach { sb.appendLine(it) }
        val text = sb.toString().take(120_000)

        val api = "https://api.github.com/repos/$repo/contents/ci-error.txt"
        var sha: String? = null
        val get = (URL(api + "?ref=$branch").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        if (get.responseCode == 200) {
            sha = get.inputStream.bufferedReader().readText()
                .let { Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").find(it)?.groupValues?.get(1) }
        }
        get.disconnect()

        val body = buildString {
            append("{\"message\":\"CI-Fehler-Erfassung (automatisch, Arena-Session)\"")
            append(",\"content\":\"${Base64.getEncoder().encodeToString(text.toByteArray())}\"")
            append(",\"branch\":\"$branch\"")
            if (sha != null) append(",\"sha\":\"$sha\"")
            append("}")
        }
        val put = (URL(api).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        put.outputStream.use { it.write(body.toByteArray()) }
        val status = put.responseCode
        put.disconnect()
        println("CI-ERROR-CAPTURE: ci-error.txt aktualisiert (HTTP $status)")
    } catch (t: Throwable) {
        println("CI-ERROR-CAPTURE fehlgeschlagen: $t")
    }
}
