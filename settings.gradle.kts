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

// DIAGNOSE-HOOK (wird entfernt): Bei Build-Fehlern wird die Fehlermeldung
// als GitHub-Actions-Workflow-Command (::error::) ausgegeben – der Runner
// erfasst das automatisch als Check-Run-Annotation, die über die API
// lesbar ist (funktioniert auch mit read-only GITHUB_TOKEN).
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    gradle.buildFinished { result ->
        val failure = result.failure
        if (failure != null) {
            try {
                val sw = java.io.StringWriter()
                failure.printStackTrace(java.io.PrintWriter(sw))
                val raw = (failure.message ?: failure.toString()) + "\n" + sw.toString().take(2500)
                val escaped = raw.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                println("::error::SECUREGUARD-BUILD-FAILURE: $escaped")
            } catch (e: Exception) {
                println("::error::SECUREGUARD-HOOK-FEHLER: ${e.message}")
            }
        }
    }
}

