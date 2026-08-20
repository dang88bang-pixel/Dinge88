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
// DIAGNOSE-HOOK (nur CI/PR-Läufe): Schreibt die Gradle-Fehlermeldung
// bei Build-Fehlern in `build-failure.log` und pusht sie in den Branch,
// damit der Fehlergrund auch ohne Zugriff auf die Actions-Logs lesbar ist.
// Wird nach der Fehlerbehebung wieder entfernt.
// ============================================================
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    gradle.buildFinished { result ->
        val failure = result.failure
        if (failure == null) return@buildFinished
        try {
            val repoDir = rootProject.projectDir
            val sw = java.io.StringWriter()
            failure.printStackTrace(java.io.PrintWriter(sw))
            val message = buildString {
                appendLine("=== SecureGuard Build-Fehler (${System.getenv("GITHUB_RUN_ID") ?: "?"}) ===")
                appendLine(failure.message ?: failure.toString())
                appendLine()
                appendLine(sw.toString().take(4000))
            }

            val logFile = java.io.File(repoDir, "build-failure.log")
            val changed = !logFile.exists() || logFile.readText() != message
            if (!changed) return@buildFinished
            logFile.writeText(message)

            fun run(vararg cmd: String): Int {
                val pb = ProcessBuilder(*cmd)
                pb.directory(repoDir)
                pb.redirectErrorStream(true)
                return pb.start().waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            }

            // Commit + Push nur, wenn sich der Inhalt geändert hat
            // (verhindert Endlosschleife über PR-synchronize-Events).
            run("git", "add", "-f", "build-failure.log")
            val committed = run(
                "git", "-c", "user.name=SecureGuard CI",
                "-c", "user.email=ci@localhost",
                "commit", "-m", "ci: build-failure.log"
            )
            if (committed == 0) {
                val headRef = System.getenv("GITHUB_HEAD_REF")
                if (!headRef.isNullOrBlank()) {
                    run("git", "push", "origin", "HEAD:$headRef")
                }
            }

            // Backup-Kanal: Fehlermeldung als PR-Kommentar posten.
            val token = System.getenv("GITHUB_TOKEN")
            val repo = System.getenv("GITHUB_REPOSITORY")
            val ref = System.getenv("GITHUB_REF")
            val pr = Regex("refs/pull/(\\d+)/").find(ref ?: "")?.groupValues?.get(1)
            if (token != null && repo != null && pr != null) {
                val body = message.take(6000)
                    .replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "")
                val jsonFile = java.io.File(repoDir, "pr-comment.json")
                jsonFile.writeText("{\"body\":\"$body\"}")
                run(
                    "curl", "-s", "-X", "POST",
                    "-H", "Authorization: Bearer $token",
                    "-H", "Accept: application/vnd.github+json",
                    "https://api.github.com/repos/$repo/issues/$pr/comments",
                    "--data-binary", "@pr-comment.json"
                )
                jsonFile.delete()
            }
        } catch (e: Exception) {
            // Der Hook darf den Build nie weiter beeinträchtigen.
            println("Diagnose-Hook-Fehler (ignoriert): ${e.message}")
        }
    }
}
