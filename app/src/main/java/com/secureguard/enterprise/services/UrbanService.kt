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
 * Urbane Infrastruktur als Ortungsquelle.
 *
 * Mögliche Quellen (Platzhalter):
 * - WiGle.net (BSSID → GPS)
 * - CKAN Open Data (Smart City)
 * - DHL/Post APIs (Paketstationen)
 * - ÖPNV (Haltestellen-/Fahrzeugdaten), Laternen, Wetterstationen
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections = _detections.asSharedFlow()

    suspend fun searchAsset(asset: Asset): Detection? {
        // TODO: Abgleich mit urbanen Open-Data-/Infrastruktur-APIs.
        return null
    }
}
