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

// ==================== CI-Diagnose-Sonde ====================
// Läuft auf GitHub Actions bei JEDDEM Build-Ende (auch bei Konfigurationsfehlern)
// und pusht die Diagnose als Branch `apk-delivery-logs`, damit Fehlerursachen
// auch dann sichtbar sind, wenn die Actions-Logs nicht abrufbar sind.
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
            } catch (e: Throwable) { listOf("taskGraph nicht verfügbar: ${e.message}") }
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
                    sb.appendLine("[${c.javaClass.name}] ${c.message ?: "(keine Meldung)"}")
                    c.stackTrace.take(25).forEach { st -> sb.appendLine("    at ${st.className}.${st.methodName}(${st.fileName}:${st.lineNumber})") }
                    c = c.cause; d++
                }
            }
            taskFailures.forEach { f -> sb.appendLine(f) }
            sb.appendLine()
            sb.appendLine("--- NETZWERK-PROBES (vom Runner) ---")
            listOf(
                "https://dl.google.com/android/repository/repository2-1.xml",
                "https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom",
                "https://plugins.gradle.org/m2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml",
                "https://services.gradle.org/distributions/",
                "https://github.com/gradle/gradle-distributions/releases/download/v8.9.0/gradle-8.9-bin.zip",
                "https://maven.google.com/com/android/tools/build/gradle/8.7.3/gradle-8.7.3.pom",
                "https://jitpack.io"
            ).forEach { u ->
                val p = ProcessBuilder("curl", "-s", "-o", "/dev/null", "-w", "%{http_code}", "--connect-timeout", "10", "-m", "25", "-L", u)
                    .redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText().trim()
                p.waitFor()
                sb.appendLine("HTTP $out  <-  $u")
            }
            sb.appendLine()
            sb.appendLine("--- WRAPPER-DISTS ---")
            val dists = java.io.File("${System.getProperty("user.home")}/.gradle/wrapper/dists")
            sb.appendLine(if (dists.exists()) dists.walk().take(30).joinToString("\n") { it.path } else "(keine)")
            sb.appendLine()
            sb.appendLine("--- GRADLE USER HOME (top) ---")
            val ghome = java.io.File("${System.getProperty("user.home")}/.gradle")
            if (ghome.exists()) ghome.listFiles()?.forEach { sb.appendLine("${it.name}/ ${if (it.isDirectory) "(dir)" else "(file, ${it.length()} B)"}") }

            // Push als Branch
            val work = java.nio.file.Files.createTempDirectory("diag").toFile()
            java.io.File(work, "DIAGNOSE.txt").writeText(sb.toString())
            fun sh(vararg cmd: String, dir: java.io.File? = null): String {
                val p = ProcessBuilder(*cmd).apply {
                    directory(dir ?: work)
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
            println("DIAGNOSE-SONDE: pushed (pushOut: $pushOut)")
        } catch (e: Throwable) {
            println("DIAGNOSE-SONDE fehlgeschlagen (ignoriert): ${e.message}")
        }
    }
}
