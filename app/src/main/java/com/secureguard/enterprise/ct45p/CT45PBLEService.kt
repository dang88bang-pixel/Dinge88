package com.secureguard.enterprise.ct45p

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.services.BleService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CT45P-spezifischer BLE-Scan: kapselt [BleService] und protokolliert
 * JEDEN Scan (Start, Ergebnis, Fehler) in der on-device Log-Datei –
 * vollständige Anfragenverfolgbarkeit auf dem Honeywell CT45P.
 */
@Singleton
class CT45PBLEService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleService: BleService,
    private val logManager: CT45PLogManager
) {

    /**
     * Führt einen BLE-Scan für [asset] aus und loggt den kompletten
     * Anfragezyklus (Start → Ergebnis → Dauer → Erfolg/Fehler).
     */
    suspend fun scanForAsset(asset: Asset, timeoutMs: Long = 30_000): Detection? {
        val startTime = System.currentTimeMillis()

        // Anfrage loggen (Start)
        logManager.logRequest(
            requestType = "BLE_SCAN",
            endpoint = "/api/search/BLE",
            parameters = mapOf(
                "mac" to asset.mac,
                "assetId" to asset.id,
                "timeout" to "${timeoutMs / 1000}s",
                "device" to "CT45P"
            )
        )

        return try {
            // BLE-Scan durchführen (delegiert an BleService)
            val result = withTimeoutOrNull(timeoutMs) { bleService.searchAsset(asset) }
            val duration = System.currentTimeMillis() - startTime

            // Ergebnis loggen
            logManager.logRequest(
                requestType = "BLE_SCAN_RESULT",
                endpoint = "/api/search/BLE",
                parameters = mapOf("mac" to asset.mac),
                response = result?.let {
                    "found=true,rssi=${it.rssi},lat=${it.latitude},lon=${it.longitude}"
                } ?: "found=false",
                durationMs = duration,
                success = result != null
            )

            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logManager.logRequest(
                requestType = "BLE_SCAN_ERROR",
                endpoint = "/api/search/BLE",
                parameters = mapOf("mac" to asset.mac),
                durationMs = duration,
                success = false,
                error = e.message
            )
            null
        }
    }
}
