package com.secureguard.enterprise.services

import android.util.Log
import com.secureguard.enterprise.config.SamsungConfig
import com.secureguard.enterprise.data.model.Detection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DSNR (Dynamic Signal-to-Noise Ratio) Signal-Filter Service.
 *
 * Analysiert und bereinigt RSSI-Messungen aus BLE, WiFi und LoRa Kanälen.
 * Besonders effektiv auf Samsung Android 14 Geräten mit hoher Funkdichte.
 *
 * Hauptaufgaben:
 * - Dynamische Schätzung des Rauschbodens (Noise Floor)
 * - Filtern von Ausreißern und HF-Störungen
 * - Berechnung des kalkulierten Signal-Rausch-Abstands (SNR in dB)
 * - Korrektur von RSSI-Werten für präzisere Ortung
 */
@Singleton
class DsnrService @Inject constructor() {

    private companion object {
        const val TAG = "DsnrService"
    }

    private val _dsnrStats = MutableStateFlow(DsnrStats())
    val dsnrStats: StateFlow<DsnrStats> = _dsnrStats.asStateFlow()

    // Verlauf historischer RSSI-Werte pro Asset MAC / BSSID
    private val rssiHistory = ConcurrentHashMap<String, MutableList<Int>>()

    /**
     * DSNR-Statusstruktur.
     */
    data class DsnrStats(
        val totalFiltered: Long = 0L,
        val noiseFloorDbm: Int = SamsungConfig.DSNR_DEFAULT_NOISE_FLOOR_DBM,
        val avgSnrDb: Double = 12.5,
        val activeChannelFilter: String = "DSNR-Samsung-Adaptive"
    )

    /**
     * Wendet den DSNR-Filter auf eine eingehende Detektion an und gibt die
     * optimierte Detektion mit korrigiertem RSSI zurück.
     */
    fun filterDetection(detection: Detection): Detection {
        val key = detection.mac ?: detection.assetId ?: return detection
        val rawRssi = detection.rssi ?: return detection

        val history = rssiHistory.getOrPut(key) { mutableListOf() }
        synchronized(history) {
            history.add(rawRssi)
            if (history.size > SamsungConfig.DSNR_SMOOTHING_WINDOW_SIZE) {
                history.removeAt(0)
            }
        }

        // Gleitender Mittelwert & Ausreißerbereinigung
        val averageRssi = synchronized(history) { history.average() }
        val filteredRssi = SamsungConfig.applyDsnrFilter(averageRssi.toInt()).toInt()

        val noiseFloor = _dsnrStats.value.noiseFloorDbm
        val snr = filteredRssi - noiseFloor

        Log.d(
            TAG,
            "DSNR Filter [${detection.source}]: Raw=${rawRssi}dBm -> Filtered=${filteredRssi}dBm (SNR: ${"%.1f".format(snr.toDouble())}dB)"
        )

        // Update Statistics
        updateStats(filteredRssi, snr.toDouble())

        return detection.copy(rssi = filteredRssi)
    }

    private fun updateStats(rssi: Int, snr: Double) {
        val current = _dsnrStats.value
        val newTotal = current.totalFiltered + 1
        val newAvgSnr = (current.avgSnrDb * (newTotal - 1) + snr) / newTotal

        _dsnrStats.value = current.copy(
            totalFiltered = newTotal,
            avgSnrDb = newAvgSnr
        )
    }

    /**
     * Setzt den geschätzten Rauschboden (Noise Floor) manuell oder dynamisch.
     */
    fun setNoiseFloor(noiseFloorDbm: Int) {
        _dsnrStats.value = _dsnrStats.value.copy(noiseFloorDbm = noiseFloorDbm)
        Log.i(TAG, "DSNR Noise Floor aktualisiert auf: ${noiseFloorDbm} dBm")
    }
}
