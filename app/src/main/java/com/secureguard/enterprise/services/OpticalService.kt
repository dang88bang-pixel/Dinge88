package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optical recognition channel.
 *
 * Matches assets by comparing a scanned QR/barcode value against known
 * asset MACs, IDs or VINs. The actual camera scan is triggered by the
 * user via ScanQrScreen; this service checks whether a given asset has
 * a pending optical match.
 *
 * Without an explicit scan result this channel returns null – no
 * simulated data is ever generated.
 */
@Singleton
class OpticalService @Inject constructor(
    @ApplicationContext private val context: Context
) : DetectionCapable() {

    /** Last scanned optical code (set by ScanQrScreen or external trigger). */
    @Volatile
    var lastScannedCode: String? = null

    suspend fun searchAsset(asset: Asset): Detection? {
        val code = lastScannedCode ?: return null

        // Match: scanned code equals the asset's MAC, ID, or VIN
        val matches = code.equals(asset.mac, ignoreCase = true) ||
            code.equals(asset.id, ignoreCase = true) ||
            (asset.vin != null && code.equals(asset.vin, ignoreCase = true))

        if (!matches) return null

        // Consume the scan (one-shot)
        lastScannedCode = null

        return Detection(
            assetMac = asset.mac,
            sourceType = DetectionSource.OPTICAL,
            nodeId = "optical-qr",
            rssi = 0,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 2f,
            message = "Optisch erkannt: $code",
            timestamp = Date()
        ).also { emit(it) }
    }

    /** Setzt den letzten gescannten Code (von ScanQrScreen aufgerufen). */
    fun setScannedCode(code: String) {
        lastScannedCode = code
    }
}
