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

// TEMPORÄRER DIAGNOSE-HOOK (wird nach Build-Verifikation entfernt)
if (System.getenv("GITHUB_EVENT_NAME") == "pull_request") {
    gradle.addBuildListener(object : org.gradle.BuildListener {
        override fun settingsEvaluated(settings: org.gradle.api.initialization.Settings) {}
        override fun projectsLoaded(gradle: org.gradle.api.invocation.Gradle) {}
        override fun projectsEvaluated(gradle: org.gradle.api.invocation.Gradle) {}
        override fun buildFinished(result: org.gradle.BuildResult) {
            val failure = result.failure
            if (failure != null) {
                try {
                    if (System.getenv("SECUREGUARD_DIAG") == "1") return
                    val repoDir = rootProject.projectDir
                    val msg = failure.message ?: ""
                    val task = Regex("task '([^']+)'").find(msg)?.groupValues?.get(1)
                        ?: ":app:assembleDebug"
                    val target = if (task.startsWith(":")) task else ":app:$task"
                    val pb = ProcessBuilder(
                        "./gradlew", target, "--offline", "--no-daemon",
                        "--console=plain", "--stacktrace"
                    )
                    pb.directory(repoDir)
                    pb.redirectErrorStream(true)
                    pb.environment()["SECUREGUARD_DIAG"] = "1"
                    val proc = pb.start()
                    val finished = proc.waitFor(240, java.util.concurrent.TimeUnit.SECONDS)
                    if (!finished) proc.destroyForcibly()
                    val output = proc.inputStream.bufferedReader().readText()
                    val core = output.lineSequence()
                        .filter {
                            it.contains("What went wrong") || it.startsWith("> ") ||
                                it.contains("minCompileSdk") || it.contains("compileSdk") ||
                                it.contains("requires") || it.contains("FAILED") ||
                                it.startsWith("e:") || it.contains("missing required") ||
                                it.contains("Could not")
                        }
                        .take(80)
                        .joinToString("\n")
                    val body = if (core.isNotBlank()) core else output.take(3000)
                    val e2 = body.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    println("::error::SECUREGUARD-DETAIL ($target): $e2")
                    var cause: Throwable? = failure
                    while (cause?.cause != null) cause = cause.cause
                    if (cause != null && cause !== failure) {
                        val e3 = (cause.message ?: "").replace("%", "%25")
                            .replace("\r", "%0D").replace("\n", "%0A")
                        println("::error::SECUREGUARD-CAUSE: $e3")
                    }
                } catch (e: Exception) {
                    println("::error::SECUREGUARD-HOOK-FEHLER: ${e.message}")
                }
            }
        }
    })
}
