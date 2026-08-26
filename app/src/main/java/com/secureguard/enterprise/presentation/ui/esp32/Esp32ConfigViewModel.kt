package com.secureguard.enterprise.presentation.ui.esp32

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.R
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class Esp32ConfigViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val auditLogService: AuditLogService,
    private val usbSerialService: com.secureguard.enterprise.services.UsbSerialService
) : ViewModel() {

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand.asStateFlow()

    private val _usbDevices = MutableStateFlow<String?>(null)
    val usbDevices: StateFlow<String?> = _usbDevices.asStateFlow()

    fun scanUsbDevices() {
        val drivers = usbSerialService.availableDrivers()
        _usbDevices.value = if (drivers.isEmpty()) {
            context.getString(R.string.no_usb_adapters)
        } else {
            drivers.joinToString("\n") { d ->
                "${d.device.deviceName} (VID:${d.device.vendorId} PID:${d.device.productId}, ${d.ports.size} Ports)"
            }
        }
    }

    fun sendConfig(targetMac: String, configJson: String) {
        viewModelScope.launch {
            _lastCommand.value = context.getString(R.string.sending_config, targetMac)
            val asset = repository.getAssetByMac(targetMac)
            if (asset != null) {
                val success = agentService.sendAction(asset, "CONFIG:$configJson")
                _lastCommand.value = if (success) {
                    context.getString(R.string.config_sent, targetMac)
                } else {
                    context.getString(R.string.config_offline_queued)
                }
                auditLogService.log(
                    action = "ESP32_CONFIG",
                    details = "CONFIG an $targetMac: $configJson"
                )
            } else {
                _lastCommand.value = context.getString(R.string.asset_not_found, targetMac)
            }
        }
    }
}
