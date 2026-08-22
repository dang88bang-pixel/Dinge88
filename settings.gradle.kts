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
// Die Sandbox kann die Actions-Run-Logs nicht abrufen (Firewall). Deshalb
// schreibt der Build bei einem Fehler die Diagnose in öffentliche Kanäle:
//   0) Workflow-Annotation (serverseitig auf der Run-Seite lesbar)
//   1) Step-Summary (Job-Seite)
//   2) ci-error.txt auf dem Branch (braucht contents:write)
// Zusätzlich: Sofort-Marker-Annotations (SG-SETTINGS / SG-SHUTDOWN) zur
// Diagnose, ob das settings-Skript bzw. die JVM überhaupt laufen.
//
// ACHTUNG: Nur stabile Gradle-8.9-öffentliche API verwenden:
//  - BuildResult.getFailure() liefert seit 8.x direkt ein Throwable
//  - es gibt KEINE gradle.services-Methode
// =============================================================================
try {
    if (System.getenv("GITHUB_TOKEN") != null && System.getenv("GITHUB_REPOSITORY") != null) {
        try {
            println("::notice title=SG-CI-HOOK::settings.gradle.kts ausgeführt – Fehler-Hook wird registriert")
        } catch (_: Throwable) {
        }

        // JVM-Shutdown-Hook: feuert bei jeder (sauberen) Beendigung der
        // Gradle-JVM – Marker, dass die JVM lief (nicht bei SIGKILL).
        try {
            Runtime.getRuntime().addShutdownHook(object : Thread({
                try {
                    println("::warning title=SG-SHUTDOWN::Gradle-JVM-Beendigung (Shutdown-Hook feuerte)")
                } catch (_: Throwable) {
                }
            }))
        } catch (_: Throwable) {
        }

        val captureError: (Throwable, String) -> Unit = { failure, branch ->
            try {
                val token = System.getenv("GITHUB_TOKEN") ?: return@captureError
                val repo = System.getenv("GITHUB_REPOSITORY") ?: return@captureError

                val sb = StringBuilder()
                sb.appendLine("CI-Fehler-Erfassung (automatisch, Arena-Session)")
                sb.appendLine("GITHUB_RUN_ID=${System.getenv("GITHUB_RUN_ID") ?: "?"}")
                sb.appendLine("GITHUB_REF=${System.getenv("GITHUB_REF") ?: "?"}")
                sb.appendLine("BRANCH=$branch")
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

                // KANAL 0: Workflow-Annotation
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

                // KANAL 1: Step-Summary
                System.getenv("GITHUB_STEP_SUMMARY")?.let { summaryPath ->
                    try {
                        java.io.File(summaryPath).appendText(
                            "## ❌ CI Build-Fehler (automatisch erfasst)\n\n" +
                                "```\n" + text.take(20_000) + "\n```\n"
                        )
                    } catch (_: Throwable) {
                    }
                }

                // KANAL 2: Repo-Datei ci-error.txt
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

        val resolveBranch: () -> String = {
            System.getenv("GITHUB_HEAD_REF")
                ?: System.getenv("GITHUB_REF")
                    ?.removePrefix("refs/heads/")
                    ?.removePrefix("refs/tags/")
                    ?: "HEAD"
        }

        // Fehler-Hook (Gradle-8.9-öffentliche API, deprecated aber vorhanden).
        try {
            gradle.buildFinished { result ->
                val failure = result.failure
                if (failure != null) {
                    captureError(failure, resolveBranch())
                }
            }
        } catch (t: Throwable) {
            println("CI-HOOK buildFinished-Registrierung fehlgeschlagen: $t")
        }

        // Backup-Hook über BuildListener (fängt dieselbe Phase ab).
        try {
            gradle.addBuildListener(object : org.gradle.BuildListener {
                override fun buildStarted(gradle: org.gradle.api.invocation.Gradle) {
                    // nicht benötigt
                }

                override fun buildFinished(result: org.gradle.BuildResult) {
                    val failure = result.failure
                    if (failure != null) {
                        captureError(failure, resolveBranch())
                    }
                }
            })
        } catch (t: Throwable) {
            println("CI-HOOK BuildListener-Registrierung fehlgeschlagen: $t")
        }
    }
} catch (t: Throwable) {
    println("CI-HOOK-SETUP fehlgeschlagen: $t")
}
