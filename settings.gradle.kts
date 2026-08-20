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

// DIAGNOSE-BISEKTION (wird entfernt): nur Marker, kein Listener.
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    println("::error::SECUREGUARD-M1")
}
println("::error::SECUREGUARD-M5-SETTINGS-END")

