import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Release-Keystore-Auflösung (Reihenfolge):
//  1. secureguard-keystore.jks im Repo-Root (lokal abgelegt)
//  2. app/secureguard-keystore.jks (Pfad des CI-"Decode Keystore"-Schritts;
//     Passwörter dann aus KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD)
//  3. Andernfalls generiert der Build deterministisch einen neuen Keystore –
//     so erzeugt `assembleRelease` IMMER eine installierbare, signierte APK
//     (kein app-release-unsigned.apk mehr). Die generierte Datei ist via
//     .gitignore (*.jks) vor unbeabsichtigtem Commit geschützt.
val keystoreFile: File
val keystorePassword: String
val keyAlias: String
val keyPassword: String

run {
    val rootKeystore = rootProject.file("secureguard-keystore.jks")
    val appKeystore = rootProject.file("app/secureguard-keystore.jks")
    val existing = listOf(rootKeystore, appKeystore).firstOrNull { it.exists() }

    if (existing != null) {
        keystoreFile = existing
        keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: "secureguard-release"
        keyAlias = System.getenv("KEY_ALIAS") ?: "secureguard"
        keyPassword = System.getenv("KEY_PASSWORD") ?: keystorePassword
    } else {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val keytool = File(
            System.getProperty("java.home"),
            "bin/keytool" + if (isWindows) ".exe" else ""
        )
        val cmd = listOf(
            keytool.absolutePath, "-genkeypair", "-v",
            "-keystore", rootKeystore.absolutePath,
            "-storetype", "PKCS12",
            "-alias", "secureguard",
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", "10950",
            "-storepass", "secureguard-release",
            "-keypass", "secureguard-release",
            "-dname", "CN=SecureGuard Enterprise, OU=Fleet, O=SecureGuard, C=DE"
        )
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0 || !rootKeystore.exists()) {
            throw GradleException("Keystore-Generierung fehlgeschlagen (exit $exit):\n$output")
        }
        logger.lifecycle("SecureGuard: kein Release-Keystore gefunden – neuer Keystore generiert: ${rootKeystore.absolutePath}")
        keystoreFile = rootKeystore
        keystorePassword = "secureguard-release"
        keyAlias = "secureguard"
        keyPassword = "secureguard-release"
    }
}

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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.secureguard.enterprise"
        // Vollständiger Support: Android 11 (API 30) bis Android 14 (API 34).
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

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
        buildConfigField("String", "MQTT_BROKER_URL", "\"${apiKey("MQTT_BROKER_URL")}\"")
        buildConfigField("String", "WEBSOCKET_URL", "\"${apiKey("WEBSOCKET_URL")}\"")
        buildConfigField("String", "MCP_SERVER_URL", "\"${apiKey("MCP_SERVER_URL")}\"")
    }

    val releaseSigning = signingConfigs.create("release") {
        storeFile = keystoreFile
        storePassword = keystorePassword
        this.keyAlias = keyAlias
        keyPassword = keyPassword
    }

    buildTypes {
        release {
            // R8: Code-Shrinking + Obfuskation + Ressourcen-Shrinking.
            // Die nötigen Keep-Regeln für reflektive Serialisierung (Gson),
            // Retrofit-Interfaces und Room-Enums stehen in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            // Release-APK ist durch die Keystore-Auflösung oben immer signiert
            // und damit direkt installierbar.
            signingConfig = releaseSigning
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

    // MQTT (Paho – clientseitiger MqttAsyncClient; der veraltete
    // org.eclipse.paho.android.service wird bewusst NICHT genutzt, siehe
    // IMPLEMENTIERUNGS_INVENTUR.md "Abweichungen")
    implementation(libs.paho.mqtt.client)

    // Location (echte GPS-Position)
    implementation(libs.play.services.location)

    // BLE – Nordic ble-ktx (optional; der aktive Scan läuft über die
    // Plattform-API in BleService.kt, siehe IMPLEMENTIERUNGS_INVENTUR.md)
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
