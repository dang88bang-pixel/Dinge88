package com.secureguard.enterprise.presentation.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.TelemetryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val telemetryService: TelemetryService
) : ViewModel() {

    private val _assets = MutableStateFlow<List<Asset>>(emptyList())
    val assets: StateFlow<List<Asset>> = _assets.asStateFlow()

    private val _selectedAsset = MutableStateFlow<Asset?>(null)
    val selectedAsset: StateFlow<Asset?> = _selectedAsset.asStateFlow()

    private val _commandLog = MutableStateFlow<List<String>>(emptyList())
    val commandLog: StateFlow<List<String>> = _commandLog.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    init {
        loadAssets()
    }

    private fun loadAssets() {
        viewModelScope.launch {
            repository.getWhitelistedAssets().collect { assetList ->
                _assets.value = assetList
                if (_selectedAsset.value == null && assetList.isNotEmpty()) {
                    _selectedAsset.value = assetList.first()
                }
            }
        }
    }

    fun selectAsset(asset: Asset) {
        _selectedAsset.value = asset
    }

    fun executeAction(actionType: ActionType) {
        viewModelScope.launch {
            val asset = _selectedAsset.value ?: return@launch
            _isExecuting.value = true

            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = when (actionType) {
                ActionType.ALARM -> {
                    val success = telemetryService.sendCommand(asset.mac, "ALARM")
                    "$timestamp → ALARM ausgelöst ${if (success) "✓" else "✗"}"
                }
                ActionType.LIGHT -> {
                    val success = telemetryService.sendCommand(asset.mac, "LIGHT")
                    "$timestamp → Lichter blinken ${if (success) "✓" else "✗"}"
                }
                ActionType.MOTOR_OFF -> {
                    val success = telemetryService.sendCommand(asset.mac, "MOTOR_OFF")
                    "$timestamp → Motor ausgeschaltet ${if (success) "✓" else "✗"}"
                }
                ActionType.BATTERY -> {
                    val success = telemetryService.sendCommand(asset.mac, "BATTERY")
                    "$timestamp → Batterie getrennt ${if (success) "✓" else "✗"}"
                }
                ActionType.MESSAGE -> {
                    val success = telemetryService.sendCommand(asset.mac, "MESSAGE")
                    "$timestamp → Nachricht gesendet ${if (success) "✓" else "✗"}"
                }
                ActionType.POSITION -> {
                    val success = telemetryService.sendCommand(asset.mac, "POSITION")
                    "$timestamp → Position angefordert ${if (success) "✓" else "✗"}"
                }
                ActionType.RESTART -> {
                    val success = telemetryService.sendCommand(asset.mac, "RESTART")
                    "$timestamp → Neustart ausgelöst ${if (success) "✓" else "✗"}"
                }
                ActionType.TELEMETRY -> {
                    val success = telemetryService.sendCommand(asset.mac, "TELEMETRY")
                    "$timestamp → Telemetrie gelesen ${if (success) "✓" else "✗"}"
                }
            }

            _commandLog.value = _commandLog.value + logEntry
            _isExecuting.value = false
        }
    }

    fun clearLog() {
        _commandLog.value = emptyList()
    }
}

enum class ActionType {
    ALARM, LIGHT, MOTOR_OFF, BATTERY, MESSAGE, POSITION, RESTART, TELEMETRY
}
