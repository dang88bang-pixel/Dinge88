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

// ==================== CI-Diagnose-Sonde (Runde 3) ====================
// Läuft auf GitHub Actions bei JEDEM Build-Ende (auch Konfigurationsfehler).
// 1) Fehlerkette als ##[error]-Annotations (via API lesbar)
// 2) Vollständige Diagnose als Branch `apk-delivery-logs`
if (System.getenv("GITHUB_ACTIONS") == "true" && !System.getenv("GITHUB_TOKEN").isNullOrBlank()) {
    val __diagStart = java.time.Instant.now()
    gradle.buildFinished {
        try {
            val failure = it.failure
            val taskFailures = try {
                gradle.taskGraph.allTasks.mapNotNull { t ->
                    val f = try { t.state.failure } catch (_: Throwable) { null }
                    if (f != null) "FAILED TASK ${t.path}: ${f.javaClass.name}: ${f.message ?: ""}" else null
                }
            } catch (e: Throwable) { listOf("taskGraph nicht verfuegbar: ${e.message}") }
            if (failure == null && taskFailures.isEmpty()) return@buildFinished

            val sb = StringBuilder()
            sb.appendLine("=== SecureGuard CI-Diagnose ${java.time.Instant.now()} (Start: $__diagStart) ===")
            sb.appendLine("Gradle: ${gradle.gradleVersion} | Run: ${System.getenv("GITHUB_RUN_ID")} | Sha: ${System.getenv("GITHUB_SHA")}")
            sb.appendLine("Tasks: ${gradle.startParameter.taskNames.joinToString(" ")}")
            sb.appendLine()
            if (failure != null) {
                sb.appendLine("--- ROOT FAILURE ---")
                var c: Throwable? = failure
                var d = 0
                while (c != null && d < 10) {
                    val line = "[${c.javaClass.name}] ${c.message ?: "(keine Meldung)"}"
                    sb.appendLine(line)
                    // In Annotations pumpen (via API abrufbar):
                    println("##[error]DIAG ${c.javaClass.simpleName}: ${c.message?.take(1200)?.replace('\n', ' ')}")
                    c.stackTrace.take(8).forEach { st ->
                        sb.appendLine("    at ${st.className}.${st.methodName}(${st.fileName}:${st.lineNumber})")
                    }
                    c = c.cause; d++
                }
            }
            taskFailures.forEach { f ->
                sb.appendLine(f)
                println("##[error]DIAG ${f.take(1200).replace('\n', ' ')}")
            }

            // Push als Branch (best effort)
            val work = java.nio.file.Files.createTempDirectory("diag").toFile()
            java.io.File(work, "DIAGNOSE.txt").writeText(sb.toString())
            fun sh(vararg cmd: String): String {
                val p = ProcessBuilder(*cmd).apply {
                    directory(work)
                    redirectErrorStream(true)
                    environment()["GIT_AUTHOR_NAME"] = "github-actions[bot]"
                    environment()["GIT_AUTHOR_EMAIL"] = "41898282+github-actions[bot]@users.noreply.github.com"
                    environment()["GIT_COMMITTER_NAME"] = "github-actions[bot]"
                    environment()["GIT_COMMITTER_EMAIL"] = "41898282+github-actions[bot]@users.noreply.github.com"
                }.start()
                val out = p.inputStream.bufferedReader().readText(); p.waitFor()
                return out.trim()
            }
            sh("git", "init", "-q", "-b", "apk-delivery-logs")
            sh("git", "add", "-f", "DIAGNOSE.txt")
            sh("git", "commit", "-q", "-m", "CI-Diagnose run=${System.getenv("GITHUB_RUN_ID")}")
            val pushOut = sh("git", "push", "-q", "--force",
                "https://x-access-token:${System.getenv("GITHUB_TOKEN")}@github.com/${System.getenv("GITHUB_REPOSITORY")}.git",
                "HEAD:refs/heads/apk-delivery-logs")
            println("DIAGNOSE-SONDE: pushed (pushOut: ${pushOut.take(300)})")
        } catch (e: Throwable) {
            println("##[error]DIAG-SONDE fehlgeschlagen (ignoriert): ${e.message}")
        }
    }
}
