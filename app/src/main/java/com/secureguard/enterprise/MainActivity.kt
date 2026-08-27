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
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.presentation.ui.common.missingBackgroundPermissions
import com.secureguard.enterprise.presentation.ui.common.missingPermissions
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.NfcService
import com.secureguard.enterprise.services.UsbSerialService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var nfcService: NfcService
    @Inject lateinit var usbSerialService: UsbSerialService

    /** Empfängt das Ergebnis der USB-Permission-Anfrage. */
    private val usbPermissionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != UsbSerialService.ACTION_USB_PERMISSION) return
            val device: android.hardware.usb.UsbDevice? =
                intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(
                android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false
            )
            android.util.Log.i(
                "MainActivity",
                "USB-Permission für ${device?.deviceName}: ${if (granted) "erteilt" else "verweigert"}"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // USB: Ergebnis-Broadcast registrieren; eingesteckte Adapter direkt anfragen.
        // RECEIVER_NOT_EXPORTED: ab targetSdk 34 Pflicht für app-eigene Broadcasts
        // (SecurityException sonst); ContextCompat kümmert sich um API-Level-Kompat.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            android.content.IntentFilter(UsbSerialService.ACTION_USB_PERMISSION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handleUsbAttachIntent(intent)
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
                // Stufe 2: Background-Location (ab Android 10, NUR nachdem
                // Fine-Location gewährt wurde – sonst wird der Dialog nicht
                // angezeigt bzw. automatisch abgelehnt).
                val bgMissing = missingBackgroundPermissions(context)
                if (bgMissing.isNotEmpty() &&
                    ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(bgMissing.toTypedArray())
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

    override fun onDestroy() {
        runCatching { unregisterReceiver(usbPermissionReceiver) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
        handleUsbAttachIntent(intent)
    }

    /**
     * USB-Seriell-Adapter erkannt (Intent-Filter USB_DEVICE_ATTACHED):
     * fehlende USB-Berechtigung automatisch per Systemdialog anfragen.
     */
    private fun handleUsbAttachIntent(intent: Intent?) {
        if (intent?.action != android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device: android.hardware.usb.UsbDevice? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    android.hardware.usb.UsbManager.EXTRA_DEVICE,
                    android.hardware.usb.UsbDevice::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE)
            }
        device ?: return
        val driver = usbSerialService.availableDrivers()
            .firstOrNull { it.device.deviceId == device.deviceId } ?: return
        if (!usbSerialService.hasPermission(driver)) {
            usbSerialService.requestPermission(driver)
        }
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
