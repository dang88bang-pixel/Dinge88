package com.secureguard.enterprise.presentation.ui.assets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.CrowdService
import com.secureguard.enterprise.services.LoraService
import com.secureguard.enterprise.services.NotificationService
import com.secureguard.enterprise.services.OpticalService
import com.secureguard.enterprise.services.SatelliteService
import com.secureguard.enterprise.services.Telemetry
import com.secureguard.enterprise.services.TelemetryService
import com.secureguard.enterprise.services.UrbanService
import com.secureguard.enterprise.services.WifiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AssetDetailViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val telemetryService: TelemetryService,
    private val wifiService: WifiService,
    private val loraService: LoraService,
    private val opticalService: OpticalService,
    private val urbanService: UrbanService,
    private val crowdService: CrowdService,
    private val satelliteService: SatelliteService,
    private val notificationService: NotificationService
) : ViewModel() {

    private val _asset = MutableStateFlow<Asset?>(null)
    val asset: StateFlow<Asset?> = _asset.asStateFlow()

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResult = MutableStateFlow<SearchResult?>(null)
    val searchResult: StateFlow<SearchResult?> = _searchResult.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private val _telemetry = MutableStateFlow<Telemetry?>(null)
    val telemetry: StateFlow<Telemetry?> = _telemetry.asStateFlow()

    fun loadAsset(assetId: String) {
        viewModelScope.launch {
            val found = repository.getAssetById(assetId)
            if (found != null) {
                _asset.value = found
                loadDetections(found.mac)
            }
        }
    }

    private fun loadDetections(mac: String) {
        viewModelScope.launch {
            repository.getDetections(mac).collect { detectionList ->
                _detections.value = detectionList
            }
        }
    }

    // ============ STATUS / ASSET ============

    fun setStatus(status: AssetStatus) {
        viewModelScope.launch {
            val asset = _asset.value ?: return@launch
            val updated = asset.copy(status = status)
            repository.updateAsset(updated)
            _asset.value = updated
            _actionResult.value = ActionResult(true, "Status auf ${status.name} gesetzt")
        }
    }

    fun setExternalAllowed(enabled: Boolean) {
        viewModelScope.launch {
            val asset = _asset.value ?: return@launch
            val updated = asset.copy(externalAllowed = enabled)
            repository.updateAsset(updated)
            _asset.value = updated
            _actionResult.value = ActionResult(
                true,
                if (enabled) "Externe Quellen erlaubt" else "Externe Quellen gesperrt"
            )
        }
    }

    fun deleteAsset() {
        viewModelScope.launch {
            val asset = _asset.value ?: return@launch
            repository.deleteAsset(asset)
            _actionResult.value = ActionResult(true, "Asset gelöscht")
            _asset.value = null
        }
    }

    // ============ AKTIONEN ============

    fun executeAction(actionType: ActionType) {
        viewModelScope.launch {
            _actionResult.value = ActionResult.Processing

            val asset = _asset.value ?: return@launch

            val result = when (actionType) {
                ActionType.ALARM -> executeAlarm(asset)
                ActionType.LIGHT -> executeLight(asset)
                ActionType.MOTOR_OFF -> executeMotorOff(asset)
                ActionType.BATTERY -> executeBattery(asset)
                ActionType.MESSAGE -> executeMessage(asset)
                ActionType.POSITION -> executePosition(asset)
            }

            _actionResult.value = result

            // Alert speichern
            if (result.success) {
                repository.insertAlert(
                    Alert(
                        assetId = asset.id,
                        type = AlertType.SECURITY,
                        severity = AlertSeverity.INFO,
                        message = "${actionType.name} erfolgreich ausgeführt",
                        timestamp = Date()
                    )
                )
                notificationService.sendActionNotification(asset, actionType.name, true)
            } else {
                repository.insertAlert(
                    Alert(
                        assetId = asset.id,
                        type = AlertType.CRITICAL,
                        severity = AlertSeverity.WARNING,
                        message = "${actionType.name} fehlgeschlagen: ${result.message}",
                        timestamp = Date()
                    )
                )
                notificationService.sendActionNotification(asset, actionType.name, false)
            }
        }
    }

    private suspend fun executeAlarm(asset: Asset): ActionResult =
        runCommand(asset.mac, "ALARM", "Alarm ausgelöst")

    private suspend fun executeLight(asset: Asset): ActionResult =
        runCommand(asset.mac, "LIGHT", "Lichter blinken")

    private suspend fun executeMotorOff(asset: Asset): ActionResult =
        runCommand(asset.mac, "MOTOR_OFF", "Motor ausgeschaltet")

    private suspend fun executeBattery(asset: Asset): ActionResult =
        runCommand(asset.mac, "BATTERY", "Batterie getrennt")

    private suspend fun executeMessage(asset: Asset): ActionResult =
        runCommand(asset.mac, "MESSAGE", "Nachricht gesendet")

    private suspend fun executePosition(asset: Asset): ActionResult =
        runCommand(asset.mac, "POSITION", "Position angefordert")

    private suspend fun runCommand(mac: String, command: String, successMessage: String): ActionResult {
        if (mac.isNotEmpty()) {
            val success = telemetryService.sendCommand(mac, command)
            return if (success) {
                ActionResult(true, successMessage)
            } else {
                ActionResult(false, "Keine Verbindung zum Gerät")
            }
        }
        return ActionResult(false, "Keine MAC-Adresse hinterlegt")
    }

    // ============ SUCHE ============

    fun startSearch() {
        val asset = _asset.value ?: return
        runSearch(
            asset,
            listOf(
                { telemetryService.searchAsset(asset) },
                { wifiService.searchAsset(asset) },
                { loraService.searchAsset(asset) },
                { opticalService.searchAsset(asset) },
                { urbanService.searchAsset(asset) },
                { if (asset.externalAllowed) crowdService.searchAsset(asset) else null },
                { satelliteService.searchAsset(asset) }
            )
        )
    }

    fun searchExternal() {
        val asset = _asset.value ?: return
        runSearch(
            asset,
            listOf(
                { if (asset.externalAllowed) crowdService.searchAsset(asset) else null }
            )
        )
    }

    fun searchSatellite() {
        val asset = _asset.value ?: return
        runSearch(asset, listOf({ satelliteService.searchAsset(asset) }))
    }

    fun searchBluetooth() {
        val asset = _asset.value ?: return
        runSearch(asset, listOf({ telemetryService.searchAsset(asset) }))
    }

    private fun runSearch(asset: Asset, searches: List<suspend () -> Detection?>) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResult.value = null

            val results = searches.map { runCatching { it() }.getOrNull() }
            val best = results.filterNotNull().minByOrNull { it.rssi }
            _searchResult.value = if (best != null) {
                repository.insertDetection(best)
                // Asset sofort als online markieren.
                val updated = asset.copy(
                    status = AssetStatus.ONLINE,
                    lastSeen = Date(),
                    latitude = best.latitude ?: asset.latitude,
                    longitude = best.longitude ?: asset.longitude,
                    rssi = best.rssi
                )
                repository.updateAsset(updated)
                _asset.value = updated
                loadDetections(asset.mac)
                SearchResult(true, best)
            } else {
                SearchResult(false)
            }
            _isSearching.value = false
        }
    }

    // ============ TELEMETRIE ============

    fun refreshTelemetry() {
        viewModelScope.launch {
            val asset = _asset.value ?: return@launch
            val telemetry = telemetryService.getLatestTelemetry(asset.mac)
            _telemetry.value = telemetry
            if (telemetry != null) {
                repository.updateAssetStatus(
                    mac = asset.mac,
                    status = AssetStatus.ONLINE,
                    timestamp = System.currentTimeMillis(),
                    lat = telemetry.latitude,
                    lon = telemetry.longitude
                )
                _actionResult.value = ActionResult(true, "Telemetrie aktualisiert")
                loadAsset(asset.id)
            } else {
                _actionResult.value = ActionResult(false, "Keine Telemetrie verfügbar (kein Gerät erreichbar)")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

enum class ActionType { ALARM, LIGHT, MOTOR_OFF, BATTERY, MESSAGE, POSITION }

data class ActionResult(
    val success: Boolean,
    val message: String
) {
    companion object {
        val Processing = ActionResult(false, "Wird ausgeführt...")
    }
}

data class SearchResult(
    val found: Boolean,
    val detection: Detection? = null
)
