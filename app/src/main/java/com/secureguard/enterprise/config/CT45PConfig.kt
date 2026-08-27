package com.secureguard.enterprise.config

import android.os.Build

/**
 * Honeywell CT45P XON – gerätespezifische Konfiguration & Kompatibilität.
 *
 * Das CT45P XON ist ein rugged Enterprise-Handheld von Honeywell, das
 * werkseitig mit **Android 11 (API 30)** ausgeliefert wird (Upgrade auf
 * Android 13 je nach Variante verfügbar). SecureGuard berücksichtigt:
 *
 * - **BLE-Scan:** Auf Android 11 (API ≤ 30) wird statt BLUETOOTH_SCAN die
 *   Standortberechtigung ACCESS_FINE_LOCATION benötigt → siehe
 *   [needsLocationForBle] und `BleService`.
 * - **WiFi-Scan:** `WifiManager.getScanResults()` benötigt auf Android 11
 *   die Standortberechtigung (in `WifiService` berücksichtigt).
 * - **MQTT (tcp):** Lokale Broker (Mosquitto) sprechen Klartext. Klartext
 *   wird über die Network-Security-Config geregelt: der **Debug**-Build
 *   erlaubt Klartext (LAN-Broker), der **Release**-Build erzwingt TLS.
 *   Für einen lokalen Klartext-Broker im Produktivbetrieb die Domain/IP in
 *   `res/xml/network_security_config.xml` (release) freigeben.
 * - **Barcode-Scanner:** Der integrierte 2D-Imager arbeitet als
 *   HID-Keyboard (Enterprise-Profile); der ZXing-Kamera-Scan bleibt
 *   zusätzlich verfügbar.
 * - **USB-Host:** Serielle Anbindung (FTDI/CP210x) via
 *   `UsbSerialService` (usb-serial-for-android).
 */
object CT45PConfig {

    const val MANUFACTURER = "Honeywell"
    const val MODEL_PREFIX = "CT45"

    // ============ BARCODE-SCAN (Enterprise-Profile) ============
    const val SCAN_TIMEOUT_MS = 5000
    const val SCAN_MODE = "AUTO_ENTER"

    // ============ GPS (Enterprise-Profile) ============
    const val GPS_UPDATE_INTERVAL_MS = 1000
    const val GPS_MIN_DISTANCE_M = 5

    // ============ ERKENNUNG ============

    /** Erkennt ein Honeywell CT45P (Hersteller + Modell-Präfix). */
    fun isCT45P(): Boolean =
        Build.MANUFACTURER.equals(MANUFACTURER, ignoreCase = true) &&
            (Build.MODEL.startsWith(MODEL_PREFIX, ignoreCase = true) ||
                Build.DEVICE.startsWith("ct45", ignoreCase = true) ||
                Build.PRODUCT.startsWith("ct45", ignoreCase = true))

    /** Kurzbeschreibung des Geräts fürs Log / About. */
    fun deviceSummary(): String =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    /**
     * Android-11-Kompatibilität: Auf API ≤ 30 benötigt der BLE-Scan die
     * Standortberechtigung; BLUETOOTH_SCAN existiert erst ab API 31.
     */
    val needsLocationForBle: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.R

    /** Benachrichtigungs-Permission (POST_NOTIFICATIONS) erst ab API 33 nötig. */
    val needsNotificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
