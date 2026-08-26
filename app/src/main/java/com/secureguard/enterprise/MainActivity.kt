package com.secureguard.enterprise

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.secureguard.enterprise.presentation.navigation.SecureGuardApp
import com.secureguard.enterprise.presentation.theme.SecureGuardTheme
import com.secureguard.enterprise.presentation.ui.auth.LockScreen
import com.secureguard.enterprise.presentation.ui.common.missingPermissions
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.NfcService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var nfcService: NfcService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // NFC-Intent beim Kaltstart verarbeiten
        intent?.let { handleNfcIntent(it) }

        setContent {
            val context = LocalContext.current
            val settingsPrefs = remember {
                context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)
            }
            var darkMode by remember {
                mutableStateOf(settingsPrefs.getBoolean("dark_mode", false))
            }
            androidx.compose.runtime.DisposableEffect(settingsPrefs) {
                val listener =
                    android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
                        if (key == "dark_mode") {
                            darkMode = prefs.getBoolean("dark_mode", false)
                        }
                    }
                settingsPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    settingsPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            // Runtime-Permissions einmalig anfragen
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Ergebnis wird von den Services zur Laufzeit geprüft */ }
            LaunchedEffect(Unit) {
                val missing = missingPermissions(context)
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
                }
            }

            SecureGuardTheme(darkTheme = darkMode) {
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
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        if (intent.action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED ||
            intent.action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            nfcService.processTag(intent)
        }
    }
}
