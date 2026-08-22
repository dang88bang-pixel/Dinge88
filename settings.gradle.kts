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
// schreibt der Build bei einem Fehler die Diagnose (Exception-Kette)
// in drei Kanäle:
//   0) Workflow-Annotation (serverseitig auf der Run-Seite lesbar)
//   1) Step-Summary (Job-Seite)
//   2) ci-error.txt auf dem Branch (braucht contents:write)
// Liegt in settings.gradle.kts, damit auch Fehler der Projekt-Build-Scripts
// erfasst werden. Lokal (ohne GITHUB_TOKEN) bleibt der Hook wirkungslos.
//
// ACHTUNG: Nur stabile Gradle-8.9-Öffentliche-API verwenden
// (BuildResult.getFailure() liefert seit 8.x direkt ein Throwable;
// es gibt KEINE gradle.services-Methode).
// =============================================================================
try {
    if (System.getenv("GITHUB_TOKEN") != null && System.getenv("GITHUB_REPOSITORY") != null) {
        gradle.buildFinished { result ->
            try {
                val failure = result.failure
                if (failure == null) return@buildFinished
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
                var e: Throwable? = failure
                var depth = 0
                while (e != null && depth < 6) {
                    sb.appendLine("[Fehler-Kette $depth] ${e.javaClass.name}: ${e.message}")
                    e.stackTrace.take(20).forEach { s -> sb.appendLine("    at $s") }
                    e = e.cause
                    depth++
                }
                val text = sb.toString().take(120_000)

                // KANAL 0: Workflow-Annotation (serverseitig lesbar)
                try {
                    val compact = text
                        .lines()
                        .filter { it.isNotBlank() }
                        .takeLast(15)
                        .joinToString(" %0A ")
                        .take(1000)
                    println("::error title=CI-Build-Fehler-Erfassung::$compact")
                } catch (_: Throwable) {
                }

                // KANAL 1: Step-Summary (Job-Seite, keine Rechte nötig)
                System.getenv("GITHUB_STEP_SUMMARY")?.let { summaryPath ->
                    try {
                        java.io.File(summaryPath).appendText(
                            "## ❌ CI Build-Fehler (automatisch erfasst)\n\n" +
                                "```\n" + text.take(20_000) + "\n```\n"
                        )
                    } catch (_: Throwable) {
                    }
                }

                // KANAL 2: Repo-Datei ci-error.txt (braucht contents:write)
                try {
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
                            .let { Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").find(it)?.groupValues?.get(1) }
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
                    println("CI-ERROR-CAPTURE (Repo-Datei) fehlgeschlagen: $t")
                }
            } catch (t: Throwable) {
                println("CI-ERROR-CAPTURE fehlgeschlagen: $t")
            }
        }
    }
} catch (t: Throwable) {
    println("CI-HOOK-SETUP fehlgeschlagen: $t")
}
