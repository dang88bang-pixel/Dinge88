package com.secureguard.enterprise.presentation.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.data.repository.SettingsRepository
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
    private val telemetryService: TelemetryService,
    val settings: SettingsRepository
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

            val command = actionType.name
            var success = if (actionType == ActionType.TELEMETRY) {
                telemetryService.getLatestTelemetry(asset.mac) != null
            } else {
                telemetryService.sendCommand(asset.mac, command)
            }
            var retried = false
            if (!success && settings.recoverResend.value) {
                retried = true
                success = if (actionType == ActionType.TELEMETRY) {
                    telemetryService.getLatestTelemetry(asset.mac) != null
                } else {
                    telemetryService.sendCommand(asset.mac, command)
                }
            }

            if (settings.commandLogging.value) {
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val description = when (actionType) {
                    ActionType.ALARM -> "ALARM ausgelöst"
                    ActionType.LIGHT -> "Lichter blinken"
                    ActionType.MOTOR_OFF -> "Motor ausgeschaltet"
                    ActionType.BATTERY -> "Batterie getrennt"
                    ActionType.MESSAGE -> "Nachricht gesendet"
                    ActionType.POSITION -> "Position angefordert"
                    ActionType.RESTART -> "Neustart ausgelöst"
                    ActionType.TELEMETRY -> "Telemetrie gelesen"
                }
                val suffix = buildString {
                    append(if (success) "✓" else "✗")
                    if (retried) append(if (success) " (nach Wiederholung)" else " (Wiederholung fehlgeschlagen)")
                }
                _commandLog.value = _commandLog.value + "$timestamp → $description $suffix"
            }
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
