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
    val listener = object : org.gradle.BuildListener {
        override fun settingsEvaluated(settings: org.gradle.api.initialization.Settings) {}
        override fun projectsLoaded(gradle: org.gradle.api.invocation.Gradle) {}
        override fun projectsEvaluated(gradle: org.gradle.api.invocation.Gradle) {}
        override fun buildFinished(result: org.gradle.BuildResult) {
            val failure = result.failure
            if (failure != null) {
                try {
                    // Rekursions-Guard: Der Sub-Gradle-Lauf erbt die Umgebung.
                    if (System.getenv("SECUREGUARD_DIAG") == "1") return
                    val repoDir = rootProject.projectDir

                    // Gescheiterte Aufgabe aus der Fehlermeldung extrahieren.
                    val msg = failure.message ?: ""
                    val taskMatch = Regex("task '([^']+)'").find(msg)
                    val task = taskMatch?.groupValues?.get(1) ?: ":app:assembleDebug"
                    val target = if (task.startsWith(":")) task else ":app:$task"

                    val pb = ProcessBuilder(
                        "./gradlew", target,
                        "--offline", "--no-daemon", "--console=plain", "--stacktrace"
                    )
                    pb.directory(repoDir)
                    pb.redirectErrorStream(true)
                    pb.environment()["SECUREGUARD_DIAG"] = "1"
                    val proc = pb.start()
                    val finished = proc.waitFor(240, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished) proc.destroyForcibly()
                    val output = proc.inputStream.bufferedReader().readText()

                    // Kernmeldung ("What went wrong"-Block) extrahieren.
                    val core = output.lineSequence()
                        .filter {
                            it.contains("What went wrong") || it.startsWith("> ") ||
                                it.contains("minCompileSdk") || it.contains("compileSdk") ||
                                it.contains("requires") || it.contains("FAILED") ||
                                it.startsWith("e:") || it.contains("SigningConfig") ||
                                it.contains("missing required")
                        }
                        .take(80)
                        .joinToString("\n")
                    val body = if (core.isNotBlank()) core else output.take(3000)
                    val e2 = body.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    println("::error::SECUREGUARD-DETAIL ($target): $e2")

                    // Fehlerursache (tiefste Exception) zusätzlich ausgeben.
                    var cause: Throwable? = failure
                    while (cause?.cause != null) cause = cause.cause
                    if (cause != null && cause !== failure) {
                        val cm = cause.message ?: ""
                        val e3 = cm.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                        println("::error::SECUREGUARD-CAUSE: $e3")
                    }
                } catch (e: Exception) {
                    println("::error::SECUREGUARD-HOOK-FEHLER: ${e.message}")
                }
            }
        }
    }
    gradle.addBuildListener(listener)
}

