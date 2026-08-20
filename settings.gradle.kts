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
// DIAGNOSE-MARKER (nur CI/PR-Läufe) – wird nach der Fehlerbehebung entfernt.
// Schreibt beim Settings-Start eine Trace-Datei und pusht sie auf den
// Branch `ci-diag-trace` (separater Branch umgeht das Detached-HEAD-
// Problem des PR-Checkouts). Erscheint der Branch, funktionieren
// Settings-Evaluierung UND Push-Kanal.
// ============================================================
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    try {
        val dir = rootProject.projectDir
        val trace = java.io.File(dir, "ci-trace.txt")
        trace.writeText(
            "SETTINGS-EVALUATED run=${System.getenv("GITHUB_RUN_ID") ?: "?"} time=${System.currentTimeMillis()}\n"
        )
        fun run(vararg cmd: String): Int {
            val pb = ProcessBuilder(*cmd)
            pb.directory(dir)
            pb.redirectErrorStream(true)
            return pb.start().waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
        }
        run("git", "add", "-f", "ci-trace.txt")
        val rc = run(
            "git", "-c", "user.name=SecureGuard CI",
            "-c", "user.email=ci@localhost",
            "commit", "-m", "ci: trace"
        )
        if (rc == 0) {
            run("git", "push", "origin", "HEAD:ci-diag-trace")
        }
    } catch (e: Exception) {
        println("Diag-Marker-Fehler (ignoriert): ${e.message}")
    }
}
