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
                    val sw = java.io.StringWriter()
                    failure.printStackTrace(java.io.PrintWriter(sw))
                    val raw = (failure.message ?: failure.toString()) + "\n" + sw.toString().take(2000)
                    val escaped = raw.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    println("::error::SECUREGUARD-BUILD-FAILURE: $escaped")

                    // Rekursions-Guard: Der Sub-Gradle-Lauf erbt die Umgebung.
                    if (System.getenv("SECUREGUARD_DIAG") == "1") return
                    val repoDir = rootProject.projectDir
                    val pb = ProcessBuilder(
                        "./gradlew", ":app:compileDebugKotlin",
                        "--offline", "--no-daemon", "--console=plain", "-q"
                    )
                    pb.directory(repoDir)
                    pb.redirectErrorStream(true)
                    pb.environment()["SECUREGUARD_DIAG"] = "1"
                    val proc = pb.start()
                    val finished = proc.waitFor(180, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished) proc.destroyForcibly()
                    val output = proc.inputStream.bufferedReader().readText()
                    val errs = output.lineSequence()
                        .filter { it.startsWith("e:") || it.contains("error:") }
                        .take(60)
                        .joinToString("\n")
                    if (errs.isNotBlank()) {
                        val e2 = errs.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                        println("::error::SECUREGUARD-KOTLIN-ERRORS: $e2")
                    } else {
                        println("::error::SECUREGUARD-KOTLIN-ERRORS: (keine e:-Zeilen) " +
                            output.take(1500).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))
                    }
                } catch (e: Exception) {
                    println("::error::SECUREGUARD-HOOK-FEHLER: ${e.message}")
                }
            }
        }
    }
    gradle.addBuildListener(listener)
}

