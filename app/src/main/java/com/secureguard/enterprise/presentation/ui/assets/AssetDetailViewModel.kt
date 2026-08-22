package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.SearchResult
import com.secureguard.enterprise.config.CT45PConfig
import com.secureguard.enterprise.ct45p.CT45PBLEService
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionResult
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.data.model.Telemetry
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.NotificationService
import com.secureguard.enterprise.services.CrowdService
import com.secureguard.enterprise.services.LoraService
import com.secureguard.enterprise.services.OpticalService
import com.secureguard.enterprise.services.SatelliteService
import com.secureguard.enterprise.services.TelemetryService
import com.secureguard.enterprise.services.UrbanService
import com.secureguard.enterprise.services.BleService
import com.secureguard.enterprise.services.WifiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchChannel { ALL, BLE, LORA, WIFI, OPTICAL, URBAN, CROWD, SATELLITE }

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val telemetryService: TelemetryService,
    private val agentService: AgentService,
    private val bleService: BleService,
    private val wifiService: WifiService,
    private val loraService: LoraService,
    private val opticalService: OpticalService,
    private val urbanService: UrbanService,
    private val crowdService: CrowdService,
    private val satelliteService: SatelliteService,
    private val notificationService: NotificationService,
    private val ct45pBleService: CT45PBLEService
) : ViewModel() {

    private val _assetState = MutableStateFlow<Asset?>(null)
    val assetState: StateFlow<Asset?> = _assetState.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResult = MutableStateFlow<SearchResult?>(null)
    val searchResult: StateFlow<SearchResult?> = _searchResult.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    private var loadedMac: String? = null

    fun loadAsset(assetId: String) {
        viewModelScope.launch {
            val found = repository.resolveAsset(assetId)
            _assetState.value = found
            if (found != null && found.mac != loadedMac) {
                loadedMac = found.mac
                // Prime the telemetry cache with a fresh read.
                refreshTelemetry()
                repository.getDetections(found.mac).collect { list ->
                    _detections.value = list
                }
            }
        }
    }

    fun executeAction(actionType: ActionType) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Processing
            val asset = _assetState.value ?: return@launch

            val success = telemetryService.sendCommand(asset.mac, actionType.wireCommand)
            val result = if (success) {
                ActionResult(true, "${actionType.label} ausgeführt")
            } else {
                ActionResult(false, "Keine Verbindung zum Asset")
            }
            _actionResult.value = result

            repository.raiseAlert(
                assetId = asset.id,
                type = if (success) AlertType.SECURITY else AlertType.CRITICAL,
                severity = if (success) AlertSeverity.INFO else AlertSeverity.WARNING,
                message = "${actionType.label}: ${result.message}"
            )
            notificationService.sendActionNotification(asset, actionType, success)
        }
    }

    /** Full multi-channel search via the self-learning agent. */
    fun startSearch() = searchOnChannel(SearchChannel.ALL)

    /** Search using only the external (crowd) channel. */
    fun startExternalSearch() = searchOnChannel(SearchChannel.CROWD)

    /** Search using only the satellite channel. */
    fun startSatelliteSearch() = searchOnChannel(SearchChannel.SATELLITE)

    private fun searchOnChannel(channel: SearchChannel) {
        viewModelScope.launch {
            _isSearching.value = true
            val asset = _assetState.value
            if (asset == null) {
                _isSearching.value = false
                return@launch
            }

            val detection = when (channel) {
                SearchChannel.ALL -> agentService.searchAsset(asset).detection
                // Auf dem CT45P läuft der BLE-Scan über den loggenden
                // Wrapper (on-device Anfragenverfolgbarkeit).
                SearchChannel.BLE ->
                    if (CT45PConfig.isCT45P()) ct45pBleService.scanForAsset(asset)
                    else bleService.searchAsset(asset)
                SearchChannel.WIFI -> wifiService.searchAsset(asset)
                SearchChannel.LORA -> loraService.searchAsset(asset)
                SearchChannel.OPTICAL -> opticalService.searchAsset(asset)
                SearchChannel.URBAN -> urbanService.searchAsset(asset)
                SearchChannel.CROWD ->
                    if (asset.externalAllowed) crowdService.searchAsset(asset) else null
                SearchChannel.SATELLITE -> satelliteService.searchAsset(asset)
            }

            if (detection != null) {
                repository.insertDetection(detection)
                repository.updateAssetStatus(
                    mac = asset.mac,
                    status = AssetStatus.ONLINE,
                    timestamp = System.currentTimeMillis(),
                    rssi = detection.rssi,
                    lat = detection.latitude,
                    lon = detection.longitude
                )
                _searchResult.value = SearchResult(found = true, detection = detection)
            } else {
                _searchResult.value = SearchResult(found = false)
            }
            _assetState.value = repository.resolveAsset(asset.id)
            _isSearching.value = false
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            val asset = _assetState.value ?: return@launch
            // Force a fresh read if nothing cached yet.
            val telemetry = telemetryService.getLatestTelemetry(asset.mac)
                ?: telemetryService.run {
                    // Trigger a search which populates the telemetry cache.
                    searchAsset(asset)
                    getLatestTelemetry(asset.mac)
                }
            _telemetry.value = telemetry
            if (telemetry != null) {
                repository.updateAssetStatus(
                    mac = asset.mac,
                    status = AssetStatus.ONLINE,
                    timestamp = System.currentTimeMillis(),
                    rssi = _assetState.value?.rssi,
                    lat = telemetry.latitude,
                    lon = telemetry.longitude
                )
                _assetState.value = repository.resolveAsset(asset.id)
            }
        }
    }

    fun clearActionResult() {
        _actionResult.value = null
    }
}
