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
import androidx.core.content.IntentCompat
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
            val device: android.hardware.usb.UsbDevice? = IntentCompat.getParcelableExtra(
                intent,
                android.hardware.usb.UsbManager.EXTRA_DEVICE,
                android.hardware.usb.UsbDevice::class.java
            )
            val granted = intent.getBooleanExtra(
                android.hardware.usb.UsbManager.EXTRA_PERMISSION_GRANTED, false
            )
            android.util.Log.i(
                "MainActivity",
                "USB-Permission für ${device?.deviceName}: ${if (granted) "erteilt" else "verweigert"}"
            )
            // Offene Ansichten (z. B. automatische Port-Ansicht im ESP32-Screen)
            // automatisch über das Ergebnis informieren → Liste aktualisiert sich.
            usbSerialService.notifyPermissionResult(device, granted)
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
                mutableStateOf(settingsPrefs.getBoolean("dark_mode", true))
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

            // Runtime-Permissions in zwei Stufen (Android 11+ verlangt, dass
            // ACCESS_BACKGROUND_LOCATION getrennt und erst NACH erteilter
            // Fine-Location angefragt wird – sonst wird der Dialog still
            // abgelehnt). Stufe 2 startet deshalb erst im Ergebnis-Callback
            // von Stufe 1, nie parallel (zwei gleichzeitige Launches würden
            // sich gegenseitig das Ergebnis überschreiben).
            val backgroundLocationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Ergebnis wird von den Services zur Laufzeit geprüft */ }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                val bgMissing = missingBackgroundPermissions(context)
                if (bgMissing.isNotEmpty()) {
                    backgroundLocationLauncher.launch(bgMissing.toTypedArray())
                }
            }
            LaunchedEffect(Unit) {
                val missing = missingPermissions(context)
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing.toTypedArray())
                } else {
                    val bgMissing = missingBackgroundPermissions(context)
                    if (bgMissing.isNotEmpty()) {
                        backgroundLocationLauncher.launch(bgMissing.toTypedArray())
                    }
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
        // IntentCompat: nutzt die typisierte API erst ab API 34 (die API-33-
        // Variante hat bekannte Bugs) und sonst den kompatiblen Legacy-Pfad.
        val device: android.hardware.usb.UsbDevice? = IntentCompat.getParcelableExtra(
            intent,
            android.hardware.usb.UsbManager.EXTRA_DEVICE,
            android.hardware.usb.UsbDevice::class.java
        )
        device ?: return
        val driver = usbSerialService.availableDrivers()
            .firstOrNull { it.device.deviceId == device.deviceId } ?: return
        usbSerialService.notifyDeviceAttached(device.deviceName)
        if (!usbSerialService.hasPermission(driver)) {
            usbSerialService.requestPermissionIfMissing(driver)
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
