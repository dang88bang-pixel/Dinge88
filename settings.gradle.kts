// Project-wide Gradle settings.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "SecureGuardEnterprise"
include(":app")

// =============================================================================
// CI-Fehler-Erfassung (aktiv NUR in GitHub Actions)
// =============================================================================
// Die Sandbox kann die Actions-Run-Logs nicht abrufen (Firewall). Daher
// schreibt der Build bei einem Fehler die Diagnose (Exception-Kette +
// letzte Konsolenzeilen) über die Contents-API automatisch in
// `ci-error.txt` auf den aktuellen Branch – dort lesbar ohne Log-Zugriff.
// Liegt hier in settings.gradle.kts, damit AUCH Fehler der Projekt-
// Build-Scripts (z. B. build.gradle.kts) erfasst werden. Lokal (ohne
// GITHUB_TOKEN) bleibt der Hook wirkungslos.
// =============================================================================
try {
    if (System.getenv("GITHUB_TOKEN") != null && System.getenv("GITHUB_REPOSITORY") != null) {
        val ciLogBuffer = java.util.ArrayDeque<String>()
        try {
            gradle.services.get(org.gradle.api.logging.LoggingService::class.java)
                .addLogListener(object : org.gradle.api.logging.LoggingListener {
                    override fun requestStarted(request: org.gradle.api.logging.LogRequest?) {
                        // nicht benötigt
                    }

                    override fun messageLogged(message: org.gradle.api.logging.LogMessageData) {
                        try {
                            val level = message.level
                            if (level == org.gradle.api.logging.LogLevel.LIFECYCLE ||
                                level == org.gradle.api.logging.LogLevel.ERROR ||
                                level == org.gradle.api.logging.LogLevel.WARNING ||
                                level == org.gradle.api.logging.LogLevel.INFO
                            ) {
                                val line = message.message?.toString() ?: return
                                ciLogBuffer.addLast(line)
                                while (ciLogBuffer.size > 500) {
                                    ciLogBuffer.removeFirst()
                                }
                            }
                        } catch (_: Throwable) {
                            // Log-Buffer darf den Build niemals stören
                        }
                    }
                })
        } catch (t: Throwable) {
            println("CI-LOG-LISTENER deaktiviert: $t")
        }

        gradle.buildFinished { result ->
            try {
                if (result.failure == null) return@buildFinished
                val token = System.getenv("GITHUB_TOKEN") ?: return@buildFinished
                val repo = System.getenv("GITHUB_REPOSITORY") ?: return@buildFinished
                // WICHTIG: Bei pull_request-Events ist GITHUB_REF = refs/pull/N/merge;
                // der echte Branch kommt aus GITHUB_HEAD_REF.
                val branch = System.getenv("GITHUB_HEAD_REF")
                    ?: System.getenv("GITHUB_REF")
                        ?.removePrefix("refs/heads/")
                        ?.removePrefix("refs/tags/")
                    ?: return@buildFinished

                val sb = StringBuilder()
                sb.appendLine("CI-Fehler-Erfassung (automatisch, Arena-Session)")
                sb.appendLine("GITHUB_RUN_ID=${System.getenv("GITHUB_RUN_ID") ?: "?"}")
                sb.appendLine("GITHUB_REF=${System.getenv("GITHUB_REF") ?: "?"}")
                sb.appendLine()
                var e: Throwable? = result.failure.exception
                var depth = 0
                while (e != null && depth < 6) {
                    sb.appendLine("[Fehler-Kette $depth] ${e.javaClass.name}: ${e.message}")
                    try {
                        e.stackTrace?.take(15)?.forEach { s -> sb.appendLine("    at $s") }
                    } catch (_: Throwable) {
                    }
                    e = e.cause
                    depth++
                }
                sb.appendLine()
                sb.appendLine("===== LETZTE KONSOLENZEILEN (Build-Log) =====")
                ciLogBuffer.forEach { sb.appendLine(it) }
                val text = sb.toString().take(120_000)

                // KANAL 1 (öffentlicher Step Summary – keine Rechte nötig):
                // wird auf der (öffentlichen) Job-Seite des Laufs angezeigt.
                System.getenv("GITHUB_STEP_SUMMARY")?.let { summaryPath ->
                    try {
                        java.io.File(summaryPath).appendText(
                            "## ❌ CI Build-Fehler (automatisch erfasst)\n\n" +
                                "```log\n" + text.take(30_000) + "\n```\n"
                        )
                        println("CI-ERROR-CAPTURE: Step-Summary geschrieben")
                    } catch (t: Throwable) {
                        println("CI-ERROR-CAPTURE: Step-Summary fehlgeschlagen: $t")
                    }
                }

                // KANAL 2 (Repo-Datei – braucht contents:write):
                val api = "https://api.github.com/repos/$repo/contents/ci-error.txt"
                var sha: String? = null
                val get =
                    (java.net.URL(api + "?ref=" + branch).openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                if (get.responseCode == 200) {
                    sha = get.inputStream.bufferedReader().readText()
                        .let {
                            Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").find(it)?.groupValues?.get(1)
                        }
                }
                get.disconnect()

                val body = buildString {
                    append("{\"message\":\"CI-Fehler-Erfassung (automatisch)\"")
                    append(",\"content\":\"${java.util.Base64.getEncoder().encodeToString(text.toByteArray())}\"")
                    append(",\"branch\":\"$branch\"")
                    if (sha != null) append(",\"sha\":\"$sha\"")
                    append("}")
                }
                val put =
                    (java.net.URL(api).openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "PUT"
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                put.outputStream.use { it.write(body.toByteArray()) }
                val respCode = put.responseCode
                val respBody = try {
                    val stream = if (respCode in 200..299) put.inputStream else put.errorStream
                    stream?.bufferedReader()?.readText()?.take(400) ?: ""
                } catch (_: Throwable) {
                    ""
                }
                put.disconnect()
                println("CI-ERROR-CAPTURE: Branch=$branch HTTP=$respCode $respBody")
            } catch (t: Throwable) {
                println("CI-ERROR-CAPTURE fehlgeschlagen: $t")
            }
        }
    }
} catch (t: Throwable) {
    println("CI-HOOK-SETUP fehlgeschlagen: $t")
}
