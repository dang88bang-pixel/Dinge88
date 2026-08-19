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
 * Urbane Infrastruktur als Ortungsquelle.
 *
 * Fragt einen konfigurierbaren Open-Data-/Infrastruktur-Endpunkt
 * (z. B. WiGle.net, CKAN/Smart-City, Paketstationen) nach der Asset-Detektion
 * ab. Ohne konfigurierten Endpunkt wird fehlertolerant `null` zurückgegeben.
 */
@Singleton
class UrbanService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fetcher: RemoteDetectionFetcher
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        val detection = fetcher.fetch(
            baseUrl = settingsRepository.urbanUrl.value,
            path = "api/v1/urban/detect",
            mac = asset.mac,
            source = DetectionSource.URBAN
        )
        detection?.let { _detections.tryEmit(it) }
        return detection
    }
}
