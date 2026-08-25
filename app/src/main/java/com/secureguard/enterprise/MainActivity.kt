package com.secureguard.enterprise

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
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
import com.secureguard.enterprise.presentation.ui.common.missingPermissions
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.NfcService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var nfcService: NfcService

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* alle Kanäle bleiben aktiv; fehlende Rechte werden beim nächsten Start erneut angefragt */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestAllPermissions()
        handleNfcIntent(intent)
        enableEdgeToEdge()
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

    override fun onResume() {
        super.onResume()
        authManager.refreshAutoLock()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    private fun requestAllPermissions() {
        val missing = missingPermissions(this)
        if (missing.isEmpty()) return
        // Hintergrund-Standort erst nach Vordergrund (Android 10+ Vorgabe),
        // alle übrigen Rechte in einem Rutsch – keine künstliche Auswahl.
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else null
        val first = missing.filter { it != background }
        if (first.isNotEmpty()) {
            permissionLauncher.launch(first.toTypedArray())
        } else if (background != null && background in missing) {
            permissionLauncher.launch(arrayOf(background))
        }
    }

    private fun handleNfcIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            nfcService.processTag(intent)
        }
    }

    private fun enableNfcForegroundDispatch() {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType("*/*") } catch (_: IntentFilter.MalformedMimeTypeException) {}
            },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        adapter.enableForegroundDispatch(this, pending, filters, null)
    }

    private fun disableNfcForegroundDispatch() {
        NfcAdapter.getDefaultAdapter(this)?.disableForegroundDispatch(this)
    }
}
