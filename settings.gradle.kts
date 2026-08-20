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

// DIAGNOSE-ZEITTEST (wird entfernt): Wenn die Build-Step-Dauer um ~20s
// steigt, werden die Settings auf dem Runner nachweislich evaluiert.
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    try {
        Thread.sleep(20_000)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

