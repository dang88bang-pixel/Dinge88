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
 * Optische Erkennung über Webcams + YOLO.
 *
 * Fragt einen konfigurierbaren Inferenz-Server (YOLO/ONNX-Proxy) nach der
 * Asset-Erkennung ab. Der Server beantwortet die Detektion; ohne konfigurierten
 * Endpunkt wird fehlertolerant `null` zurückgegeben.
 */
@Singleton
class OpticalService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fetcher: RemoteDetectionFetcher
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        val detection = fetcher.fetch(
            baseUrl = settingsRepository.opticalUrl.value,
            path = "api/v1/optical/detect",
            mac = asset.mac,
            source = DetectionSource.OPTICAL
        )
        detection?.let { _detections.tryEmit(it) }
        return detection
    }
}
