package com.secureguard.enterprise.presentation.ui.esp32

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.services.UsbSerialEvent
import com.secureguard.enterprise.services.UsbSerialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class Esp32ConfigViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val auditLogService: AuditLogService,
    private val usbSerialService: UsbSerialService
) : ViewModel() {

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastCommand = MutableStateFlow<String?>(null)
    val lastCommand: StateFlow<String?> = _lastCommand.asStateFlow()

    private val _usbDevices = MutableStateFlow<String?>(null)
    val usbDevices: StateFlow<String?> = _usbDevices.asStateFlow()

    private val _usbStatus = MutableStateFlow<String?>(null)
    val usbStatus: StateFlow<String?> = _usbStatus.asStateFlow()

    init {
        // Automatische Port-Ansicht: Liste direkt beim Öffnen prüfen und danach
        // bei jedem Anstecken/Berechtigungsergebnis ohne manuellen Scan
        // aktualisieren (F: automatisch, kein Berechtigungs-/Aktionsaufwand).
        scanUsbDevices()
        viewModelScope.launch {
            usbSerialService.events.collect { event ->
                when (event) {
                    is UsbSerialEvent.PermissionResult -> {
                        if (event.granted) {
                            _usbStatus.value = "✅ Berechtigung für " +
                                (event.deviceName ?: "Adapter") +
                                " erteilt – Liste automatisch aktualisiert"
                        } else {
                            _usbStatus.value = "❌ USB-Berechtigung für " +
                                (event.deviceName ?: "Adapter") +
                                " abgelehnt – Port-Zugriff nicht möglich"
                        }
                        scanUsbDevices()
                    }
                    is UsbSerialEvent.DeviceAttached -> {
                        _usbStatus.value = "🔌 ${event.deviceName} angesteckt – " +
                            "Liste automatisch aktualisiert"
                        scanUsbDevices()
                    }
                }
            }
        }
    }

    /**
     * Scannt angeschlossene USB-Seriell-Adapter samt Ports. Fehlt einem
     * Adapter die USB-Berechtigung, wird sie automatisch angefragt
     * (Systemdialog, mit Cooldown) – kein manueller Umweg über die
     * Systemeinstellungen nötig.
     */
    fun scanUsbDevices() {
        val drivers = usbSerialService.availableDrivers()
        if (drivers.isEmpty()) {
            _usbDevices.value = "Keine USB-Serial-Adapter gefunden"
            return
        }
        _usbDevices.value = drivers.joinToString("\n") { d ->
            val permissionMark = if (usbSerialService.hasPermission(d)) "✅" else "🔒"
            "$permissionMark ${d.device.deviceName} (VID:${d.device.vendorId} " +
                "PID:${d.device.productId}, ${d.ports.size} Ports)" +
                if (usbSerialService.hasPermission(d)) "" else " · Berechtigung fehlt"
        }
        // Fehlende Berechtigung automatisch anfragen (einmalig, mit Cooldown).
        val firstMissing = drivers.firstOrNull { !usbSerialService.hasPermission(it) }
        if (firstMissing != null && usbSerialService.requestPermissionIfMissing(firstMissing)) {
            _usbStatus.value = "🔒 USB-Berechtigung für ${firstMissing.device.deviceName} " +
                "wird automatisch angefragt …"
        }
    }

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
