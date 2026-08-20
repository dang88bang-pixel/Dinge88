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

// ============================================================
// DIAGNOSE (nur CI/PR-Läufe) – wird nach der Fehlerbehebung entfernt.
// 1) Marker beim Settings-Start: beweist, dass die Settings evaluiert
//    wurden und der Git-Push-Kanal aus dem Runner funktioniert.
// 2) buildFinished-Hook: pusht die Gradle-Fehlermeldung als
//    build-failure.log in den Branch, postet sie als PR-Kommentar
//    und als Check-Run – mehrere unabhängige Kanäle.
// ============================================================

fun diagPush(repoDir: java.io.File, files: List<String>): Boolean {
    return try {
        fun run(vararg cmd: String): Int {
            val pb = ProcessBuilder(*cmd)
            pb.directory(repoDir)
            pb.redirectErrorStream(true)
            return pb.start().waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
        }
        for (f in files) run("git", "add", "-f", f)
        val committed = run(
            "git", "-c", "user.name=SecureGuard CI",
            "-c", "user.email=ci@localhost",
            "commit", "-m", "ci: diagnose-output"
        )
        if (committed != 0) return false
        val headRef = System.getenv("GITHUB_HEAD_REF")
        if (headRef.isNullOrBlank()) return false
        run("git", "push", "origin", "HEAD:$headRef") == 0
    } catch (e: Exception) {
        false
    }
}

fun diagComment(repoDir: java.io.File, body: String) {
    try {
        val token = System.getenv("GITHUB_TOKEN") ?: return
        val repo = System.getenv("GITHUB_REPOSITORY") ?: return
        val ref = System.getenv("GITHUB_REF") ?: return
        val pr = Regex("refs/pull/(\\d+)/").find(ref)?.groupValues?.get(1) ?: return
        val escaped = body.take(6000)
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "")
        val json = java.io.File(repoDir, "pr-comment.json")
        json.writeText("{\"body\":\"$escaped\"}")
        val pb = ProcessBuilder(
            "curl", "-s", "-X", "POST",
            "-H", "Authorization: Bearer $token",
            "-H", "Accept: application/vnd.github+json",
            "https://api.github.com/repos/$repo/issues/$pr/comments",
            "--data-binary", "@pr-comment.json"
        )
        pb.directory(repoDir)
        pb.redirectErrorStream(true)
        pb.start().waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        json.delete()
    } catch (e: Exception) {
        // ignorieren
    }
}

fun diagCheckRun(repoDir: java.io.File, message: String) {
    try {
        val token = System.getenv("GITHUB_TOKEN") ?: return
        val repo = System.getenv("GITHUB_REPOSITORY") ?: return
        val sha = runCatching {
            ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(repoDir).redirectErrorStream(true)
                .start().inputStream.bufferedReader().readText().trim()
        }.getOrNull() ?: return
        val escaped = message.take(6000)
            .replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "")
        val json = java.io.File(repoDir, "checkrun.json")
        json.writeText(
            "{\"name\":\"CI-Diagnose\",\"head_sha\":\"$sha\",\"status\":\"completed\"," +
                "\"conclusion\":\"failure\",\"output\":{\"title\":\"Gradle-Build-Fehler\"," +
                "\"summary\":\"$escaped\",\"text\":\"$escaped\"}}"
        )
        val pb = ProcessBuilder(
            "curl", "-s", "-X", "POST",
            "-H", "Authorization: Bearer $token",
            "-H", "Accept: application/vnd.github+json",
            "https://api.github.com/repos/$repo/check-runs",
            "--data-binary", "@checkrun.json"
        )
        pb.directory(repoDir)
        pb.redirectErrorStream(true)
        pb.start().waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
        json.delete()
    } catch (e: Exception) {
        // ignorieren
    }
}

val diagDir: java.io.File = rootProject.projectDir

// Marker 1: Settings wurden evaluiert (fester Inhalt → keine Endlosschleife).
try {
    val trace = java.io.File(diagDir, "ci-trace.txt")
    if (!trace.exists()) {
        trace.writeText("SETTINGS-EVALUATED\n")
        diagPush(diagDir, listOf("ci-trace.txt"))
    }
} catch (e: Exception) {
    println("Diag-Marker-Fehler (ignoriert): ${e.message}")
}

// Marker 2: buildFinished-Hook mit Fehlerberichterstattung.
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    gradle.buildFinished { result ->
        val failure = result.failure
        if (failure == null) return@buildFinished
        try {
            val sw = java.io.StringWriter()
            failure.printStackTrace(java.io.PrintWriter(sw))
            val message = buildString {
                appendLine("=== SecureGuard Build-Fehler ===")
                appendLine(failure.message ?: failure.toString())
                appendLine()
                appendLine(sw.toString().take(4000))
            }

            val logFile = java.io.File(diagDir, "build-failure.log")
            val changed = !logFile.exists() || logFile.readText() != message
            if (changed) {
                logFile.writeText(message)
                diagPush(diagDir, listOf("build-failure.log"))
            }
            diagComment(diagDir, message)
            diagCheckRun(diagDir, message)
        } catch (e: Exception) {
            println("Diagnose-Hook-Fehler (ignoriert): ${e.message}")
        }
    }
}
