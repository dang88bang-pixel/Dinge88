package com.secureguard.enterprise.presentation.ui.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.BleService
import com.secureguard.enterprise.services.HealthMonitorService
import com.secureguard.enterprise.services.NfcService
import com.secureguard.enterprise.services.SatelliteService
import com.secureguard.enterprise.services.UsbSerialService
import com.secureguard.enterprise.services.WifiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Vier Zustände (§16): Healthy, Warning, Error, Unknown. */
enum class HealthLevel { HEALTHY, WARNING, ERROR, UNKNOWN }

/** Eine Komponente der System-Health-Ansicht. */
data class HealthItem(
    val label: String,
    val level: HealthLevel,
    val detail: String
)

/** Basis-Komponenten §16 (App, DB, Agent, Netzwerk, BLE, WiFi, GPS, USB, NFC, Backend). */
val BASE_HEALTH_COMPONENTS = listOf(
    "App", "Datenbank", "Agent", "Netzwerk", "BLE", "WiFi", "GPS", "USB", "NFC", "Backend"
)

data class HealthUiState(
    val items: List<HealthItem> = emptyList(),
    val checkedAt: String = "–",
    val loading: Boolean = false,
    val assets: Int = 0,
    val detections: Int = 0,
    val openAlerts: Int = 0,
    val agentRunning: Boolean = false
) {
    val heartbeatPaused: Boolean
        get() = items.any { it.level == HealthLevel.ERROR } ||
            items.count { it.level == HealthLevel.WARNING } >= 2
}

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthMonitorService: HealthMonitorService,
    private val bleService: BleService,
    private val wifiService: WifiService,
    private val satelliteService: SatelliteService,
    private val usbSerialService: UsbSerialService,
    private val nfcService: NfcService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthUiState())
    val uiState: StateFlow<HealthUiState> = _uiState.asStateFlow()

    private val _health = MutableStateFlow<HealthMonitorService.SystemHealth?>(null)
    val health: StateFlow<HealthMonitorService.SystemHealth?> = _health.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            if (_loading.value) return@launch
            _loading.value = true
            val system = runCatching { healthMonitorService.snapshot() }.getOrNull()
            _health.value = system
            _uiState.value = buildUiState(system)
            _loading.value = false
        }
    }

    /** Baut die 10 Komponenten mit Text/Icon-Status (nie nur Farbe). */
    private fun buildUiState(system: HealthMonitorService.SystemHealth?): HealthUiState {
        val items = mutableListOf<HealthItem>()

        // App
        items += HealthItem("App", Healthy, "läuft")

        // Datenbank
        items += HealthItem(
            "Datenbank",
            if (system == null) levelOf(null) else levelOf(system.assetCount >= 0),
            system?.let { "${it.assetCount} Assets · ${it.detectionCount} Detektionen" } ?: "keine Daten"
        )

        // Agent
        val agentRunning = system?.agentRunning == true
        items += HealthItem(
            "Agent",
            if (agentRunning) Healthy else Warning,
            if (agentRunning) "läuft · Zyklus ${system?.agentCycle ?: 0}" else "gestoppt"
        )

        // Netzwerk
        val backendComponent = system?.components?.firstOrNull { it.id == "backend" }
        items += HealthItem(
            "Netzwerk",
            when {
                backendComponent?.ok == true -> Healthy
                system?.components?.any { it.id == "mqtt" && it.ok } == true -> Warning
                system == null -> Unknown
                else -> Error
            },
            backendComponent?.detail?.take(80) ?: "offline"
        )

        // BLE
        items += HealthItem("BLE", if (bleService.hasHardware()) Healthy else Warning, if (bleService.hasHardware()) "Hardware vorhanden" else "Hardware nicht erkannt")

        // WiFi
        items += HealthItem("WiFi", if (wifiService.hasHardware()) Healthy else Warning, if (wifiService.hasHardware()) "Hardware vorhanden" else "Hardware nicht erkannt")

        // GPS
        items += HealthItem("GPS", if (satelliteService.hasHardware()) Healthy else Warning, if (satelliteService.hasHardware()) "Hardware vorhanden" else "Hardware nicht erkannt")

        // USB
        val usbDrivers = runCatching { usbSerialService.availableDrivers().size }.getOrDefault(0)
        items += HealthItem("USB", if (usbDrivers > 0) Healthy else Unknown, "$usbDrivers Adapter erkannt")

        // NFC
        items += HealthItem("NFC", if (nfcService.isAvailable()) Healthy else Unknown, if (nfcService.isAvailable()) "Adapter vorhanden" else "kein NFC-Adapter")

        // Backend
        items += HealthItem(
            "Backend",
            when {
                backendComponent == null -> Unknown
                backendComponent.ok -> Healthy
                else -> Error
            },
            backendComponent?.detail?.take(80) ?: "nicht geprüft"
        )

        val ts = system?.checkedAt?.let {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: "–"

        return HealthUiState(
            items = items,
            checkedAt = ts,
            loading = false,
            assets = system?.assetCount ?: 0,
            detections = system?.detectionCount ?: 0,
            openAlerts = system?.openAlerts ?: 0,
            agentRunning = agentRunning
        )
    }

    private fun levelOf(ok: Boolean?): HealthLevel = when (ok) {
        true -> HealthLevel.HEALTHY
        false -> HealthLevel.ERROR
        null -> HealthLevel.UNKNOWN
    }

    companion object {
        val Healthy = HealthLevel.HEALTHY
        val Warning = HealthLevel.WARNING
        val Error = HealthLevel.ERROR
        val Unknown = HealthLevel.UNKNOWN
    }
}
