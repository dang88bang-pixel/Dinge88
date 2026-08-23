package com.secureguard.enterprise.presentation.ui.esp32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class Esp32ConfigViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val auditLogService: AuditLogService
) : ViewModel() {

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand.asStateFlow()

    fun sendConfig(targetMac: String, configJson: String) {
        viewModelScope.launch {
            _lastCommand.value = "Sende CONFIG an $targetMac..."
            val asset = repository.getAssetByMac(targetMac)
            if (asset != null) {
                val success = agentService.sendAction(asset, "CONFIG:$configJson")
                _lastCommand.value = if (success) {
                    "✅ CONFIG gesendet an $targetMac"
                } else {
                    "⚠️ CONFIG in Offline-Queue (keine Verbindung)"
                }
                auditLogService.log(
                    action = "ESP32_CONFIG",
                    details = "CONFIG an $targetMac: $configJson"
                )
            } else {
                _lastCommand.value = "❌ Asset $targetMac nicht gefunden"
            }
        }
    }
}
