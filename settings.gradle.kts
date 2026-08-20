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
// erfasst das automatisch als Check-Run-Annotation (read-only-tauglich).
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    gradle.addBuildListener(object : org.gradle.api.invocation.BuildListener {
        override fun buildStarted() {}
        override fun settingsEvaluated(settings: org.gradle.api.initialization.Settings) {}
        override fun projectsLoaded(gradle: org.gradle.api.invocation.Gradle) {}
        override fun projectsEvaluated(gradle: org.gradle.api.invocation.Gradle) {}
        override fun buildFinished(result: org.gradle.api.invocation.BuildResult) {
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
    })
}

