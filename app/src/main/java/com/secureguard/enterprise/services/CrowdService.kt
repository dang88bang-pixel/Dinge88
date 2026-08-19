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
 * Crowdsourcing über Apple/Google "Find My" – NUR mit expliziter Einwilligung.
 *
 * Der Zugriff erfolgt ausschließlich, wenn `asset.externalAllowed == true` ist.
 * Fragt dann einen konfigurierbaren Find-My-Proxy (z. B. OpenHaystack-Backend)
 * nach der Asset-Position ab. Ohne konfigurierten Endpunkt → `null`.
 */
@Singleton
class CrowdService @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val fetcher: RemoteDetectionFetcher
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        // Nur bei expliziter Einwilligung – DSGVO-konform.
        if (!asset.externalAllowed) return null

        val detection = fetcher.fetch(
            baseUrl = settingsRepository.crowdUrl.value,
            path = "api/v1/crowd/detect",
            mac = asset.mac,
            source = DetectionSource.CROWD
        )
        detection?.let { _detections.tryEmit(it) }
        return detection
    }
}
