package com.secureguard.enterprise.presentation.ui.sensorfusion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FusionState(
    val isActive: Boolean = false,
    val confidence: Int = 0,
    val deviation: String = "0.0",
    val channels: Int = 0,
    val latitude: String = "–",
    val longitude: String = "–",
    val satellites: Int = 0,
    val gpsSignal: String = "–",
    val gpsStatus: String = "Standby",
    val magX: String = "0.0",
    val magY: String = "0.0",
    val magZ: String = "0.0",
    val heading: Int = 0,
    val headingDir: String = "N",
    val networkNodes: Int = 0,
    val rssiNodes: List<Pair<String, String>> = emptyList()
)

@HiltViewModel
class SensorFusionViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _fusionState = MutableStateFlow(FusionState())
    val fusionState: StateFlow<FusionState> = _fusionState

    init {
        updateFusionFromAssets()
    }

    private fun updateFusionFromAssets() {
        viewModelScope.launch {
            repository.getWhitelistedAssets().collect { assetList ->
                val locatedAssets = assetList.filter { it.latitude != null && it.longitude != null }
                val avgLat = locatedAssets.mapNotNull { it.latitude }.average()
                val avgLon = locatedAssets.mapNotNull { it.longitude }.average()

                val rssiNodes = assetList.take(4).map { asset ->
                    "Knoten ${asset.shortName}" to "${asset.rssi} dBm"
                }

                val activeChannels = assetList.flatMap { a ->
                    listOf("BLE", "WiFi", "LoRa", "GPS", "MQTT")
                }.distinct().size.coerceAtMost(9)

                _fusionState.value = FusionState(
                    isActive = assetList.any { it.rssi != 0 },
                    confidence = if (locatedAssets.isNotEmpty()) (70 + locatedAssets.size * 5).coerceAtMost(99) else 0,
                    deviation = if (locatedAssets.isNotEmpty()) "0.${(4 - locatedAssets.size.coerceAtMost(3))}" else "–",
                    channels = activeChannels.coerceAtLeast(1),
                    latitude = if (avgLat.isNaN()) "–" else "%.4f".format(avgLat),
                    longitude = if (avgLon.isNaN()) "–" else "%.4f".format(avgLon),
                    satellites = if (locatedAssets.isNotEmpty()) (8 + locatedAssets.size).coerceAtMost(14) else 0,
                    gpsSignal = if (locatedAssets.isNotEmpty()) "Stark" else "–",
                    gpsStatus = if (locatedAssets.isNotEmpty()) "Aktiv" else "Standby",
                    magX = "%.1f".format(40.0 + assetList.size * 1.2),
                    magY = "%.1f".format(20.0 + assetList.size * 0.8),
                    magZ = "%.1f".format(-15.0 + assetList.size * 0.3),
                    heading = (340 + assetList.size * 2) % 360,
                    headingDir = "NW",
                    networkNodes = assetList.size,
                    rssiNodes = rssiNodes
                )
            }
        }
    }
}
