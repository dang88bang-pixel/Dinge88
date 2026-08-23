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
 * Urban infrastructure channel: smart-city sensors, public transport hubs,
 * charging stations and partner networks.
 *
 * Queries OpenChargeMap (charging stations), DHL (pack stations) and CKAN
 * (smart-city datasets) near the asset's last known position. A detection
 * is generated when infrastructure nodes are found nearby.
 *
 * Returns null when no urban infrastructure is found near the asset.
 */
@Singleton
class UrbanService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiServiceManager: ApiServiceManager
) : DetectionCapable() {

    suspend fun searchAsset(asset: Asset): Detection? {
        val lat = asset.latitude ?: return null
        val lon = asset.longitude ?: return null

        // 1. Check charging stations nearby
        val stations = apiServiceManager.searchViaOpenChargeMap(lat, lon)
        if (stations.isNotEmpty()) {
            val nearest = stations.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "charger-${nearest.id}",
                rssi = -75,
                latitude = nearest.latitude ?: lat,
                longitude = nearest.longitude ?: lon,
                accuracyMeters = 50f,
                message = "Ladesäule: ${nearest.operator ?: nearest.title ?: "Unbekannt"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        // 2. Check DHL pack stations nearby
        val packstations = apiServiceManager.searchViaDHL(lat, lon)
        if (packstations.isNotEmpty()) {
            val nearest = packstations.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "dhl-${nearest.id ?: "unknown"}",
                rssi = -80,
                latitude = nearest.latitude ?: lat,
                longitude = nearest.longitude ?: lon,
                accuracyMeters = 80f,
                message = "Packstation: ${nearest.name ?: nearest.id ?: "?"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        // 3. Check CKAN smart-city datasets
        val datasets = apiServiceManager.searchViaCKAN(asset.mac)
        if (datasets.isNotEmpty()) {
            val ds = datasets.first()
            return Detection(
                assetMac = asset.mac,
                sourceType = DetectionSource.URBAN,
                nodeId = "ckan-${ds.id ?: "unknown"}",
                rssi = 0,
                latitude = lat,
                longitude = lon,
                accuracyMeters = 100f,
                message = "Smart-City: ${ds.title ?: "Datensatz gefunden"}",
                timestamp = Date()
            ).also { emit(it) }
        }

        return null
    }
}
