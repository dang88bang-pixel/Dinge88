package com.secureguard.enterprise

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.secureguard.enterprise.data.repository.SettingsRepository
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
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.state.collectAsState()
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
