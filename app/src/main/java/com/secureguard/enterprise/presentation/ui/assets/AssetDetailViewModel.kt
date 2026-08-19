package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.SearchResult
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionResult
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.TelemetryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val telemetryService: TelemetryService,
    private val agentService: AgentService
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

    private var loadedMac: String? = null

    fun loadAsset(assetId: String) {
        viewModelScope.launch {
            val found = repository.resolveAsset(assetId)
            _assetState.value = found
            if (found != null && found.mac != loadedMac) {
                loadedMac = found.mac
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
        }
    }

    fun startSearch() {
        viewModelScope.launch {
            _isSearching.value = true
            val asset = _assetState.value
            if (asset == null) {
                _isSearching.value = false
                return@launch
            }
            val result = agentService.searchAsset(asset)
            _searchResult.value = result
            if (result.found && result.detection != null) {
                repository.updateAssetStatus(
                    mac = asset.mac,
                    status = AssetStatus.ONLINE,
                    timestamp = System.currentTimeMillis(),
                    rssi = result.detection.rssi,
                    lat = result.detection.latitude,
                    lon = result.detection.longitude
                )
            }
            _assetState.value = repository.resolveAsset(asset.id)
            _isSearching.value = false
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch {
            val asset = _assetState.value ?: return@launch
            val telemetry = telemetryService.getLatestTelemetry(asset.mac)
            if (telemetry != null) {
                repository.updateAssetStatus(
                    mac = asset.mac,
                    status = AssetStatus.ONLINE,
                    timestamp = System.currentTimeMillis(),
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
