package com.secureguard.enterprise.presentation.ui.sensorfusion

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.NfcService
import com.secureguard.enterprise.services.UsbSerialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Eine Sensorquelle mit Quelle + letzter Aktualisierungszeit (§12). */
data class SensorChannel(
    val source: DetectionSource,
    val label: String,
    val detected: Boolean,
    val lastCount: Int,
    val lastUpdate: String
)

data class FusionUIBundle(
    val sources: List<SensorChannel> = emptyList(),
    val lastDetection: Detection? = null,
    val nfcAvailable: Boolean = false,
    val usbDevices: Int = 0,
    val error: String? = null
)

@HiltViewModel
class SensorFusionViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val nfcService: NfcService,
    private val usbSerialService: UsbSerialService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    val lastCheck = MutableStateFlow("–")

    val uiState: StateFlow<FusionUIBundle> = combine(
        repository.getAllDetections(),
        lastCheck
    ) { detections, _ -> build(detections) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FusionUIBundle())

    private fun build(detections: List<Detection>): FusionUIBundle {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val newest = detections.maxByOrNull { it.timestamp }
        val sources = DetectionSource.entries
            .filter { src ->
                src in listOf(
                    DetectionSource.BLE, DetectionSource.WIFI,
                    DetectionSource.LORA, DetectionSource.NFC, DetectionSource.OPTICAL,
                    DetectionSource.URBAN, DetectionSource.CROWD, DetectionSource.SATELLITE,
                    DetectionSource.MQTT, DetectionSource.WEBSOCKET, DetectionSource.TELEMETRY
                )
            }
            .map { src ->
                val bySource = detections.filter { it.sourceType == src }
                val last = bySource.maxByOrNull { it.timestamp }
                SensorChannel(
                    source = src,
                    label = src.name,
                    detected = last != null,
                    lastCount = bySource.size,
                    lastUpdate = last?.timestamp?.let { fmt.format(it) } ?: "–"
                )
            }
        return FusionUIBundle(
            sources = sources,
            lastDetection = newest,
            nfcAvailable = nfcService.isAvailable(),
            usbDevices = runCatching { usbSerialService.availableDrivers().size }.getOrDefault(0),
            error = null
        )
    }

    fun refresh() {
        lastCheck.update {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }
}
