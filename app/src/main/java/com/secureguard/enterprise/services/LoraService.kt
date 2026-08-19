package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LoRa / LoRaWAN – Langstreckenkommunikation (generisch, ohne Meshtastic).
 *
 * Fragt einen konfigurierbaren LoRaWAN-Backend-Endpunkt (z. B. TTN/Helium/LNS
 * Proxy) nach der Asset-Detektion ab. Ohne konfigurierten Endpunkt wird
 * fehlertolerant `null` zurückgegeben.
 */
@Singleton
class LoraService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fetcher: RemoteDetectionFetcher
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        val detection = fetcher.fetch(
            baseUrl = settingsRepository.loRaUrl.value,
            path = "api/v1/detect",
            mac = asset.mac,
            source = DetectionSource.LORA
        )
        detection?.let { _detections.tryEmit(it) }
        return detection
    }
}
