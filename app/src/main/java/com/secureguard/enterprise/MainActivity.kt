package com.secureguard.enterprise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.secureguard.enterprise.presentation.navigation.SecureGuardApp
import com.secureguard.enterprise.presentation.theme.SecureGuardTheme
import com.secureguard.enterprise.presentation.ui.auth.LockScreen
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.NfcService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var nfcService: NfcService

    /**
     * Runtime-Berechtigungen (BLE/WiFi-Scan, Standort, Benachrichtigungen).
     * Ohne explizite Erlaubnis funktionieren die Detection-Kanäle nicht –
     * deshalb werden die fehlenden Berechtigungen beim Start angefragt.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Ergebnis wird bei nächster Aktion erneut geprüft */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        setContent {
            SecureGuardTheme {
                val authState by authManager.state.collectAsState()
                if (authState.enabled && authState.locked) {
                    LockScreen(
                        attemptsRemaining = authState.attemptsRemaining,
                        onUnlock = { pin -> authManager.unlock(pin) }
                    )
                } else {
                    SecureGuardApp()
                }
            }
        }
    }

    /** Fragt alle für den Multi-Kanal-Suchbetrieb nötigen Runtime-Berechtigungen an. */
    private fun requestRuntimePermissions() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
        // Hintergrund-Standort getrennt anfragen (Android 9+/13+ Regeln):
        // erst wenn FINE_LOCATION erteilt wurde, sonst wird die Anfrage ignoriert.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    override fun onResume() {
        super.onResume()
        authManager.refreshAutoLock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // NFC-Tag verarbeiten, falls ein Tag gelesen wurde.
        if (intent.action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            nfcService.processTag(intent)
        }
    }
}
