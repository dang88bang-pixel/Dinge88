import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Keystore: CI decodes to app/secureguard-keystore.jks; local may use root.
// Env KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD or local.properties.
val keystoreFile = sequenceOf(
    rootProject.file("app/secureguard-keystore.jks"),
    rootProject.file("secureguard-keystore.jks"),
    rootProject.file("release-keystore.jks")
).firstOrNull { it.exists() } ?: rootProject.file("app/secureguard-keystore.jks")

fun propOrEnv(name: String, env: String = name): String =
    System.getenv(env)
        ?: (project.findProperty(name) as? String)
        ?: run {
            val lp = rootProject.file("local.properties")
            if (lp.exists()) {
                val p = java.util.Properties().apply { lp.inputStream().use { load(it) } }
                p.getProperty(name)
            } else null
        }
        ?: ""

val keystorePassword = propOrEnv("KEYSTORE_PASSWORD")
val keyAlias = propOrEnv("KEY_ALIAS").ifBlank { "secureguard" }
val keyPassword = propOrEnv("KEY_PASSWORD").ifBlank { keystorePassword }

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
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // ============ EXTERNE API-KEYS & ENDPUNKTE (BuildConfig) ============
        // Werte aus gradle.properties / local.properties (siehe local.properties.example).
        // Leere Werte sind erlaubt – die zugehörigen Aufrufe liefern dann null/leer
        // bzw. nutzen Runtime-Overrides aus den App-Einstellungen.
        buildConfigField("String", "WIGLE_API_KEY", "\"${apiKey("WIGLE_API_KEY")}\"")
        buildConfigField("String", "OPEN_CHARGE_MAP_KEY", "\"${apiKey("OPEN_CHARGE_MAP_KEY")}\"")
        buildConfigField("String", "NETATMO_TOKEN", "\"${apiKey("NETATMO_TOKEN")}\"")
        buildConfigField("String", "GOOGLE_API_KEY", "\"${apiKey("GOOGLE_API_KEY")}\"")
        buildConfigField("String", "MQTT_BROKER_URL", "\"${apiKey("MQTT_BROKER_URL")}\"")
        buildConfigField("String", "MQTT_USERNAME", "\"${apiKey("MQTT_USERNAME")}\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"${apiKey("MQTT_PASSWORD")}\"")
        buildConfigField("String", "WEBSOCKET_URL", "\"${apiKey("WEBSOCKET_URL")}\"")
        buildConfigField("String", "MCP_SERVER_URL", "\"${apiKey("MCP_SERVER_URL")}\"")
        buildConfigField("String", "BACKEND_BASE_URL", "\"${apiKey("BACKEND_BASE_URL")}\"")
        buildConfigField("String", "LORA_GATEWAY_URL", "\"${apiKey("LORA_GATEWAY_URL")}\"")
        buildConfigField("String", "YOLO_SERVER_URL", "\"${apiKey("YOLO_SERVER_URL")}\"")
        buildConfigField("String", "OPEN_DATA_API_URL", "\"${apiKey("OPEN_DATA_API_URL")}\"")
        buildConfigField("String", "FIND_MY_PROXY_URL", "\"${apiKey("FIND_MY_PROXY_URL")}\"")
    }

    if (keystoreFile.exists() && keystorePassword.isBlank()) {
        logger.warn(
            "SECUREGUARD: Keystore '${keystoreFile}' existiert, aber KEYSTORE_PASSWORD ist leer – " +
                "packageRelease würde mit kryptischem Fehler scheitern. Setze KEYSTORE_PASSWORD/KEY_PASSWORD."
        )
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

    // Room: Schemas nach app/schemas exportieren (Voraussetzung für Migration-Tests)
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
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

    // Room + SQLCipher (at-rest encryption)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

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

    // JSON (Moshi + Gson)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    kapt(libs.moshi.kotlin.codegen)
    implementation(libs.gson)

    // MQTT (Paho – clientseitiger MqttAsyncClient; der veraltete
    // org.eclipse.paho.android.service wird bewusst NICHT genutzt, siehe
    // IMPLEMENTIERUNGS_INVENTUR.md "Abweichungen")
    implementation(libs.paho.mqtt.client)

    // Location (echte GPS-Position)
    implementation(libs.play.services.location)

    // Coil (Bildladen)
    implementation(libs.coil.compose)

    // RxJava (Rx-Adapter für Retrofit; rxandroid wird nicht genutzt und entfernt)
    implementation(libs.rxjava)

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

    // Unit tests (JVM + Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)

    // Instrumented / Compose UI tests (Gerät oder Emulator)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
