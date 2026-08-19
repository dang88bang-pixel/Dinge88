package com.secureguard.enterprise.services

import android.content.Context
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crowdsourcing über Apple/Google "Find My" – NUR mit expliziter Einwilligung.
 *
 * Der Zugriff erfolgt ausschließlich, wenn `asset.externalAllowed == true` ist.
 * Platzhalter für OpenHaystack / Google Find My Device API.
 */
@Singleton
class CrowdService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        // Nur bei expliziter Einwilligung – DSGVO-konform.
        if (!asset.externalAllowed) return null
        // TODO: Apple/Google Find My Integration.
        return null
    }
}
