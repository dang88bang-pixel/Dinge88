package com.secureguard.enterprise.config

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Samsung Android 14 (API 34/35) & DSNR / ALC Client Gerätekonfiguration.
 *
 * Spezifische Optimierungen für Samsung One UI 6.0/6.1 (Android 14+):
 * - **Samsung Knox & One UI Battery Saver:** Vermeidet aggressives Beenden des
 *   Foreground Services durch Akkuschonung und Knox-Restriktionen.
 * - **DSNR (Dynamic Signal-to-Noise Ratio):** Dynamische Signal-Rausch-Abstands-
 *   Filterung für BLE, WiFi und LoRa RSSI-Messungen auf Samsung-Hardware.
 * - **ALC (Application Layer Control) Client:** Protokoll-Client für echtzeitnahes
 *   Telemetrie-Streaming und adaptive Kanalsteuerung unter Android 14 API 34+.
 */
object SamsungConfig {

    const val MANUFACTURER = "Samsung"

    // ============ DSNR (Dynamic Signal-to-Noise Ratio) CONFIG ============
    const val DSNR_DEFAULT_NOISE_FLOOR_DBM = -95
    const val DSNR_MIN_SNR_THRESHOLD_DB = 6.0
    const val DSNR_SMOOTHING_WINDOW_SIZE = 5
    const val DSNR_SAMSUNG_GAIN_OFFSET_DB = 3.5

    // ============ ALC (Application Layer Control) CLIENT CONFIG ============
    const val ALC_CLIENT_VERSION = "2.4.0-ALC"
    const val ALC_KEEPALIVE_INTERVAL_SEC = 15
    const val ALC_MAX_RETRY_COUNT = 5
    const val ALC_FRAME_COMPRESSION_ENABLED = true

    // ============ ERKENNUNG & STATUS ============

    /** Erkennt ein Samsung-Gerät (Herstellerprüfung). */
    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals(MANUFACTURER, ignoreCase = true)

    /** Erkennt Android 14 (API 34) oder neuer. */
    fun isAndroid14OrHigher(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // API 34

    /** Prüft, ob das Gerät ein Samsung-Handheld mit Android 14+ ist. */
    fun isSamsungAndroid14(): Boolean = isSamsungDevice() && isAndroid14OrHigher()

    /** Prüft den Akku-Optimierungsstatus für Samsung background services. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * Kurzbeschreibung der Samsung DSNR / ALC Konfiguration für System-Logs.
     */
    fun deviceSummary(context: Context): String {
        val samsung = if (isSamsungDevice()) "Samsung" else Build.MANUFACTURER
        val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val batteryOpt = if (isIgnoringBatteryOptimizations(context)) "Akkusparen aus" else "Akkusparen aktiv"
        val dsnrStatus = "DSNR: SNR ≥ ${DSNR_MIN_SNR_THRESHOLD_DB}dB"
        val alcStatus = "ALC Client: v$ALC_CLIENT_VERSION"

        return "$samsung ${Build.MODEL} · $androidVer · $batteryOpt · $dsnrStatus · $alcStatus"
    }

    /**
     * Filtert rohes RSSI mit dem DSNR-Algorithmus (Dynamic Signal-to-Noise Ratio).
     * Berechnet den korrigierten Signalwert unter Berücksichtigung von Rauschboden
     * und Samsung-Antennen-Verstärkung.
     */
    fun applyDsnrFilter(rawRssi: Int, noiseFloorDbm: Int = DSNR_DEFAULT_NOISE_FLOOR_DBM): Double {
        val gainCorrected = rawRssi + if (isSamsungDevice()) DSNR_SAMSUNG_GAIN_OFFSET_DB else 0.0
        val snr = gainCorrected - noiseFloorDbm
        return if (snr >= DSNR_MIN_SNR_THRESHOLD_DB) {
            gainCorrected
        } else {
            gainCorrected - (DSNR_MIN_SNR_THRESHOLD_DB - snr) * 0.5
        }
    }
}
