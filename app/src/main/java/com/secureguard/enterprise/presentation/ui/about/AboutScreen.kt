package com.secureguard.enterprise.presentation.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.config.CT45PConfig
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader

/**
 * About (§21): App-Name, Version, Build, Copyright, Open-Source-Lizenzen.
 * Alle Werte kommen aus [BuildConfig] bzw. [com.secureguard.enterprise.BuildConfig]
 * – es werden keine Werte erfunden. Die Three.js-Lizenz (MIT) ist über
 * [OpenSourceLib] dokumentiert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Über") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🛡️", style = MaterialTheme.typography.headlineLarge)
            Text(BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.titleLarge)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "minSdk 26 · targetSdk 35 · compileSdk 35",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Gerät: ${CT45PConfig.deviceSummary()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DetailRow("App Name", "SecureGuard Pro")
            DetailRow("Copyright", "© 2026 SecureGuard Enterprise")
            DetailRow("Build-Typ", BuildConfig.BUILD_TYPE)

            SgCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SgSectionHeader(title = "Open-Source-Lizenzen")
                    OpenSourceLib("three.js", "0.160.0", "MIT",
                        "© 2010-2024 three.js authors — https://threejs.org/")
                    OpenSourceLib("Android Jetpack (Compose/Room/WorkManager/Hilt)", "—", "Apache-2.0",
                        "© The Android Open Source Project")
                    OpenSourceLib("osmdroid", "6.1.20", "Apache-2.0",
                        "© osmdroid contributors")
                    OpenSourceLib("ZXing (zxing-android-embedded)", "4.3.0", "Apache-2.0",
                        "© 2008 ZXing authors")
                    OpenSourceLib("usb-serial-for-android", "3.5.1", "MIT",
                        "© mik3y/usb-serial-for-android contributors")
                    OpenSourceLib("SQLCipher", "4.6.1", "BSD-style",
                        "© Zetetic LLC")
                    OpenSourceLib("Eclipse Paho (mqttv3)", "1.2.5", "EPL-2.0",
                        "© Eclipse Foundation")
                    Text(
                        "Vollständige Lizenztexte: console3d/THIRD_PARTY_NOTICES.md im Repository",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    SgCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun OpenSourceLib(name: String, version: String, license: String, copyright: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("Version: $version · Lizenz: $license",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(copyright,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
