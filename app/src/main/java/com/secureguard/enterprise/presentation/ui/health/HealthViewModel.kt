package com.secureguard.enterprise.presentation.ui.health

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.HealthMonitorService
import com.secureguard.enterprise.services.NfcService
import com.secureguard.enterprise.services.UsbSerialService
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

    /** Baut die Komponenten aus echten Quellen (nie nur Farbe, immer Text/Icon). */
    private fun buildUiState(system: HealthMonitorService.SystemHealth?): HealthUiState {
        val component = { id: String ->
            system?.components?.firstOrNull { it.id == id }
        }
        val db = component("db")
        val agent = component("agent")
        val mqtt = component("mqtt")
        val backend = component("backend")

        val items = mutableListOf<HealthItem>()

        // App (läuft, wenn dieser ViewModel existiert).
        items += HealthItem("App", HealthLevel.HEALTHY, "läuft")

        // Datenbank (Room/SQLCipher).
        items += HealthItem(
            "Datenbank",
            when {
                db == null -> HealthLevel.UNKNOWN
                db.ok -> HealthLevel.HEALTHY
                else -> HealthLevel.ERROR
            },
            db?.detail ?: "nicht geprüft"
        )

        // Agent.
        val agentRunning = system?.agentRunning == true
        items += HealthItem(
            "Agent",
            if (agentRunning) HealthLevel.HEALTHY else HealthLevel.WARNING,
            if (agentRunning) "läuft · Zyklus ${system?.agentCycle ?: 0}" else "gestoppt"
        )

        // Netzwerk (MQTT-Verbindung + konfigurierte URL).
        items += HealthItem(
            "Netzwerk",
            when {
                mqtt?.detail?.startsWith("verbunden") == true -> HealthLevel.HEALTHY
                mqtt?.ok == true -> HealthLevel.WARNING
                mqtt == null -> HealthLevel.UNKNOWN
                else -> HealthLevel.ERROR
            },
            mqtt?.detail ?: "nicht geprüft"
        )

        // BLE (via BluetoothManager – echte Adapter-Hardware).
        val bleAdapter = runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }.getOrNull()
        items += HealthItem(
            "BLE",
            if (bleAdapter != null) HealthLevel.HEALTHY else HealthLevel.WARNING,
            if (bleAdapter != null) "BLE-Adapter vorhanden" else "kein BLE-Adapter"
        )

        // WiFi (via WifiManager – echte Hardware).
        val wifiManager = runCatching {
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        }.getOrNull()
        items += HealthItem(
            "WiFi",
            if (wifiManager != null) HealthLevel.HEALTHY else HealthLevel.WARNING,
            if (wifiManager != null) "WiFi-Hardware vorhanden" else "kein WiFi"
        )

        // GPS (via LocationManager – echter Provider).
        val gpsAvailable = runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            lm?.getProvider(LocationManager.GPS_PROVIDER) != null
        }.getOrDefault(false)
        items += HealthItem(
            "GPS",
            if (gpsAvailable) HealthLevel.HEALTHY else HealthLevel.WARNING,
            if (gpsAvailable) "GPS-Provider vorhanden" else "kein GPS-Provider"
        )

        // USB-Seriell (treibergestützt, echte Treiberliste).
        val usbDrivers = runCatching { usbSerialService.availableDrivers().size }.getOrDefault(0)
        items += HealthItem(
            "USB",
            when {
                usbDrivers > 0 -> HealthLevel.HEALTHY
                else -> HealthLevel.UNKNOWN
            },
            "$usbDrivers Adapter erkannt"
        )

        // NFC.
        items += HealthItem(
            "NFC",
            if (nfcService.isAvailable()) HealthLevel.HEALTHY else HealthLevel.UNKNOWN,
            if (nfcService.isAvailable()) "Adapter vorhanden" else "kein NFC-Adapter"
        )

        // Backend (HTTP-Health).
        items += HealthItem(
            "Backend",
            when {
                backend == null -> HealthLevel.UNKNOWN
                backend.ok -> HealthLevel.HEALTHY
                else -> HealthLevel.ERROR
            },
            backend?.detail?.take(90) ?: "nicht geprüft"
        )

        val ts = system?.checkedAt?.let {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(it))
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
}
