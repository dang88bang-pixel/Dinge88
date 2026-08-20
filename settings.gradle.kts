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

// DIAGNOSE-MARKER (wird entfernt): ::error::-Print + 20s-Sleep.
// Erscheint die Annotation "SECUREGUARD-MARKER-SETTINGS-RUNNING", laufen
// die Settings und der Annotation-Kanal funktioniert.
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    println("::error::SECUREGUARD-MARKER-SETTINGS-RUNNING")
    try {
        Thread.sleep(20_000)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}

