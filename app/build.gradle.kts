import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Keystore from environment (CI) or local.properties; falls back to the debug
// keystore so `assembleRelease` always produces an installable signed APK.
val keystoreFile = rootProject.file("secureguard-keystore.jks")
val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
val keyAlias = System.getenv("KEY_ALIAS") ?: "secureguard"
val keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword

/**
 * Reads an API key from gradle.properties / local.properties / -P args
 * (never committed). Supports both `local.properties` and `gradle.properties`.
 */
fun apiKey(name: String): String {
    (project.findProperty(name) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) {
        val props = Properties().apply {
            localProps.inputStream().use { load(it) }
        }
        return props.getProperty(name) ?: ""
    }
    return ""
}

android {
    namespace = "com.secureguard.enterprise"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secureguard.enterprise"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ============ EXTERNE API-KEYS (BuildConfig) ============
        // Werte kommen aus gradle.properties / local.properties (siehe local.properties.example).
        // Leere Werte sind erlaubt – die zugehörigen API-Aufrufe liefern dann null/leer.
        buildConfigField("String", "WIGLE_API_KEY", "\"${apiKey("WIGLE_API_KEY")}\"")
        buildConfigField("String", "OPEN_CHARGE_MAP_KEY", "\"${apiKey("OPEN_CHARGE_MAP_KEY")}\"")
        buildConfigField("String", "NETATMO_TOKEN", "\"${apiKey("NETATMO_TOKEN")}\"")
        buildConfigField("String", "GOOGLE_API_KEY", "\"${apiKey("GOOGLE_API_KEY")}\"")
        buildConfigField("String", "HELIUM_API_KEY", "\"${apiKey("HELIUM_API_KEY")}\"")
        buildConfigField("String", "MQTT_BROKER_URL", "\"${apiKey("MQTT_BROKER_URL")}\"")
        buildConfigField("String", "WEBSOCKET_URL", "\"${apiKey("WEBSOCKET_URL")}\"")
        buildConfigField("String", "MCP_SERVER_URL", "\"${apiKey("MCP_SERVER_URL")}\"")
    }

    val releaseSigning = signingConfigs.create("release") {
        if (keystoreFile.exists()) {
            storeFile = keystoreFile
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            keyPassword = keyPassword
        } else {
            // No release keystore supplied — sign with the debug key so the
            // release APK is installable. Override via KEYSTORE_* in CI.
            val debugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    // Nur signieren, wenn wirklich eine Keystore-Datei existiert (auf
    // GitHub-Runnern gibt es z. B. keinen ~/.android/debug.keystore).
    // Ohne Keystore wird die Release-APK unsigniert gebaut
    // (app-release-unsigned.apk) – so schlägt packageRelease nicht fehl.
    val debugKeystorePath = file("${System.getProperty("user.home")}/.android/debug.keystore")
    val hasAnyKeystore = keystoreFile.exists() || debugKeystorePath.exists()

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasAnyKeystore) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // WorkManager (Hintergrund-Agent)
    implementation(libs.androidx.work.runtime.ktx)

    // Paging (Lazy Loading)
    implementation(libs.paging.runtime.ktx)
    implementation(libs.paging.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Networking (Retrofit + OkHttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.retrofit.adapter.rxjava3)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)

    // JSON (Moshi + Gson)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    kapt(libs.moshi.kotlin.codegen)
    implementation(libs.gson)

    // MQTT (Paho MqttAsyncClient, ohne veralteten android.service)
    implementation(libs.paho.mqtt.client)

    // Location (echte GPS-Position)
    implementation(libs.play.services.location)

    // BLE – Nordic ble-ktx (Scan über Plattform-API in BleService)
    implementation(libs.nordic.ble.ktx)

    // Permissions (Accompanist – optional, Plattform-Permissions util vorhanden)
    implementation(libs.accompanist.permissions)

    // Coil (Bildladen)
    implementation(libs.coil.compose)

    // RxJava (asynchrone Operationen / Rx-Adapter für Retrofit)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)

    // USB/Serial (kabelgebundene Anbindung)
    implementation(libs.usb.serial)

    // Material / AppCompat (AppCompat theme required by ZXing CaptureActivity)
    implementation(libs.material)

    // OpenStreetMap
    implementation(libs.osmdroid.android)

    // ZXing (QR code scanning)
    implementation(libs.zxing.embedded)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Unit tests
    testImplementation(libs.junit)

    // Instrumented tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// GitHub Release erwartet genau diese Datei: release.apk
android.applicationVariants.configureEach {
    if (buildType.name == "release") {
        outputs.configureEach {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "release.apk"
        }
    }
}

// ==================== APK-Delivery via Git-Branch ====================
// Nur auf GitHub Actions aktiv: Nach jedem assemble*-Lauf (auch bei Fehlern,
// dank finalizedBy) werden APK + Prüfsummen + Build-Info als Orphan-Branch
// `apk-delivery-<release|debug>` ins Repo gepusht (force). Der Branch ist der
// zuverlässige Download-Kanal (git fetch origin apk-delivery-release).
val isCi = System.getenv("GITHUB_ACTIONS") == "true"
val ghToken = System.getenv("GITHUB_TOKEN")
val ghRepo = System.getenv("GITHUB_REPOSITORY")
val ghRunId = System.getenv("GITHUB_RUN_ID") ?: ""
val ghSha = System.getenv("GITHUB_SHA") ?: ""

tasks.register("publishApkDelivery") {
    group = "build"
    description = "Publiziert die gebaute APK + Prüfsummen als apk-delivery-* Git-Branch (nur CI)."
    doLast {
        if (!isCi || ghToken.isNullOrBlank() || ghRepo.isNullOrBlank()) {
            logger.lifecycle("publishApkDelivery: nicht auf GitHub Actions – übersprungen.")
            return@doLast
        }
        fun sh(cmd: List<String>, dir: File? = null): Pair<Int, String> {
            val p = ProcessBuilder(cmd).apply {
                directory(dir ?: rootProject.projectDir)
                redirectErrorStream(true)
                environment()["GIT_AUTHOR_NAME"] = "github-actions[bot]"
                environment()["GIT_AUTHOR_EMAIL"] = "41898282+github-actions[bot]@users.noreply.github.com"
                environment()["GIT_COMMITTER_NAME"] = "github-actions[bot]"
                environment()["GIT_COMMITTER_EMAIL"] = "41898282+github-actions[bot]@users.noreply.github.com"
            }.start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            return code to out.trim()
        }
        try {
            val wantsRelease = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            val buildType = if (wantsRelease) "release" else "debug"
            val apkDir = file("build/outputs/apk/$buildType")
            val apks = apkDir.listFiles { f: File -> f.isFile && f.extension == "apk" }?.sortedBy { it.name } ?: emptyList()
            logger.lifecycle("publishApkDelivery: buildType=$buildType, gefundene APKs=${apks.map { it.name }}")

            val workDir = Files.createTempDirectory("apkdelivery").toFile()
            val dist = File(workDir, "apk-dist").apply { mkdirs() }

            // 1) APKs kopieren, große splitten (GitHub blockt Blobs > 100 MB)
            apks.forEach { apk ->
                val target = File(dist, apk.name)
                apk.copyTo(target, overwrite = true)
                if (target.length() > 90_000_000) {
                    val (c, o) = sh(listOf("split", "-b", "90M", "-d", target.absolutePath, target.absolutePath + ".part-"))
                    if (c != 0) logger.warn("split fehlgeschlagen: $o") else target.delete()
                }
            }

            // 2) Prüfsummen
            val sums = StringBuilder()
            dist.listFiles { f: File -> f.isFile }?.sortedBy { it.name }?.forEach { f ->
                val (c, o) = sh(listOf("sha256sum", f.name), dir = dist)
                if (c == 0) sums.appendLine(o)
            }
            File(dist, "SHA256SUMS.txt").writeText(sums.toString())

            // 3) Build-Info + Diagnose
            val diag = StringBuilder()
            gradle.taskGraph.allTasks.forEach { t ->
                val failed = try { t.state.failure } catch (_: Throwable) { null }
                if (failed != null) {
                    diag.appendLine("FAILED TASK: ${t.path}")
                    var cause: Throwable? = failed
                    var depth = 0
                    while (cause != null && depth < 6) {
                        diag.appendLine("  ${cause.javaClass.simpleName}: ${cause.message?.take(2000)}")
                        cause = cause.cause
                        depth++
                    }
                }
            }
            val info = buildString {
                appendLine("buildType      : $buildType")
                appendLine("versionName    : ${android.defaultConfig.versionName}")
                appendLine("versionCode    : ${android.defaultConfig.versionCode}")
                appendLine("minSdk         : ${android.defaultConfig.minSdk}")
                appendLine("targetSdk      : ${android.defaultConfig.targetSdk}")
                appendLine("compileSdk     : ${android.compileSdk}")
                appendLine("ciRunId        : $ghRunId")
                appendLine("commit         : $ghSha")
                appendLine("timestamp (UTC): ${Instant.now()}")
                appendLine()
                if (diag.isNotEmpty()) {
                    appendLine("=== DIAGNOSE (fehlgeschlagene Tasks) ===")
                    appendLine(diag)
                } else {
                    appendLine("BUILD ERFOLGREICH")
                }
            }
            File(dist, "BUILD-INFO.txt").writeText(info)

            // 4) Orphan-Branch committen + pushen
            val branch = "apk-delivery-$buildType"
            val remote = "https://x-access-token:$ghToken@github.com/$ghRepo.git"
            var (c, o) = sh(listOf("git", "init", "-q", "-b", branch, workDir.absolutePath))
            if (c != 0) throw GradleException("git init: $o")
            sh(listOf("git", "-C", workDir.absolutePath, "add", "-f", "apk-dist")).let { (cc, oo) ->
                if (cc != 0) throw GradleException("git add: $oo")
            }
            val commitMsg = "APK-Delivery: buildType=$buildType run=$ghRunId sha=${ghSha.take(7)}"
            sh(listOf("git", "-C", workDir.absolutePath, "commit", "-q", "-m", commitMsg)).let { (cc, oo) ->
                if (cc != 0) throw GradleException("git commit: $oo")
            }
            val (pc, po) = sh(listOf("git", "-C", workDir.absolutePath, "push", "-q", "--force", remote, "HEAD:refs/heads/$branch"))
            if (pc != 0) throw GradleException("git push (${po.take(500)})")
            logger.lifecycle("publishApkDelivery: OK -> Branch '$branch' (run=$ghRunId). Dateien: ${dist.listFiles()?.joinToString { it.name }}")
        } catch (e: Exception) {
            logger.warn("publishApkDelivery FEHLGESCHLAGEN (Build selbst bleibt unberührt): ${e.message}")
        }
    }
}

tasks.configureEach {
    if (name == "assembleDebug" || name == "assembleRelease") {
        finalizedBy("publishApkDelivery")
    }
}
