package com.secureguard.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.secureguard.enterprise.presentation.navigation.SecureGuardApp
import com.secureguard.enterprise.presentation.theme.SecureGuardTheme
import com.secureguard.enterprise.presentation.ui.auth.LockScreen
import com.secureguard.enterprise.presentation.ui.settings.SettingsViewModel
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.NfcService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var nfcService: NfcService

    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Dark-Mode-Kette: Einstellungen (persistiert) steuern das Theme live.
            val settings by settingsViewModel.uiState.collectAsState()
            SecureGuardTheme(darkTheme = settings.darkMode) {
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

    override fun onResume() {
        super.onResume()
        authManager.refreshAutoLock()
        // Verbindungswerte im Einstellungs-Screen aktuell halten.
        settingsViewModel.refreshConnectionStatus()
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
