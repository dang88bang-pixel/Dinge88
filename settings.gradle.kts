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

// ==================== CI-Diagnose-Sonde (Gradle-8-kompatibel) ====================
// Registriert die Task `ciDiagnose` und haengt sie per finalizedBy an
// assembleDebug/assembleRelease. Läuft auch dann, wenn andere Tasks
// scheitern, und pumpt die Fehlerketten als ##[error]-Annotations sowie
// (best effort) als Branch apk-delivery-logs ins Repo.
if (System.getenv("GITHUB_ACTIONS") == "true") {
    gradle.rootProject {
        tasks.register("ciDiagnose") {
            group = "build"
            description = "Pumpt Task-Fehler als Annotations/Branch (nur CI)."
            doLast {
                val failures = try {
                    gradle.taskGraph.allTasks.mapNotNull { t ->
                        val f = try { t.state.failure } catch (_: Throwable) { null }
                        if (f != null) Triple(t.path, f, t.state.executing) else null
                    }
                } catch (e: Throwable) {
                    println("##[error]DIAG taskGraph: ${e.message}")
                    emptyList()
                }
                val sb = StringBuilder("=== SecureGuard CI-Diagnose ${java.time.Instant.now()} ===\n")
                sb.appendLine("Gradle: ${gradle.gradleVersion} | Run: ${System.getenv("GITHUB_RUN_ID")} | Sha: ${System.getenv("GITHUB_SHA")}")
                sb.appendLine("Tasks: ${gradle.startParameter.taskNames.joinToString(" ")}")
                if (failures.isEmpty()) {
                    sb.appendLine("Keine Task-Fehler (falls der Build scheiterte: Konfigurationsphase).")
                    println("##[error]DIAG: ciDiagnose lief, aber KEINE Task-Fehler -> Konfigurationsphase?")
                }
                for ((path, f, _) in failures) {
                    sb.appendLine("FAILED TASK $path")
                    var c: Throwable? = f
                    var d = 0
                    while (c != null && d < 10) {
                        val line = "[${c.javaClass.name}] ${c.message ?: "(keine Meldung)"}"
                        sb.appendLine("  $line")
                        println("##[error]DIAG $path :: ${c.javaClass.simpleName}: ${c.message?.take(1200)?.replace('\n', ' ')}")
                        c.stackTrace.take(5).forEach { st -> sb.appendLine("      at ${st.className}.${st.methodName}(${st.fileName}:${st.lineNumber})") }
                        c = c.cause; d++
                    }
                }
                try {
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
                    println("DIAGNOSE-SONDE: pushOut=${pushOut.take(300)}")
                } catch (e: Throwable) {
                    println("DIAGNOSE-SONDE push ignoriert: ${e.message}")
                }
            }
        }
        tasks.whenTaskAdded {
            if (name == "assembleDebug" || name == "assembleRelease") {
                finalizedBy("ciDiagnose")
            }
        }
    }
}
