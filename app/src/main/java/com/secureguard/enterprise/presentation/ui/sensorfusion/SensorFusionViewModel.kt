package com.secureguard.enterprise.presentation.ui.sensorfusion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val error: String? = null,
    // Echte GNSS-Daten über LocationManager/GnssStatus (Android 11 / API 30).
    val gnssTracking: Boolean = false,
    val satellitesInView: Int = 0,
    val satellitesUsedInFix: Int = 0
)

@HiltViewModel
class SensorFusionViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val nfcService: NfcService,
    private val usbSerialService: UsbSerialService,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    val lastCheck = MutableStateFlow("–")

    /**
     * Satellitenzustand, aktualisiert durch den echten
     * [GnssStatus.Callback] des Systems (satellitesInView / satellitesUsedInFix).
     * StateFlow (thread-sicher für den Binder-Thread des GNSS-Callbacks).
     */
    private val _satellitesInView = MutableStateFlow(0)
    private val _satellitesUsedInFix = MutableStateFlow(0)
    private val _gnssTracking = MutableStateFlow(false)

    private val locationManager: LocationManager? by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }
    private var gnssCallback: GnssStatus.Callback? = null

    init {
        startGnssTracking()
    }

    val uiState: StateFlow<FusionUIBundle> = combine(
        repository.getAllDetections(),
        lastCheck,
        _gnssTracking,
        _satellitesInView,
        _satellitesUsedInFix
    ) { detections, _, tracking, inView, usedInFix ->
        build(detections, tracking, inView, usedInFix)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FusionUIBundle())

    private fun build(
        detections: List<Detection>,
        tracking: Boolean,
        inView: Int,
        usedInFix: Int
    ): FusionUIBundle {
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
            error = null,
            gnssTracking = tracking,
            satellitesInView = inView,
            satellitesUsedInFix = usedInFix
        )
    }

    fun refresh() {
        lastCheck.update {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        }
    }

    /** Registriert den echten GNSS-Status-Callback (Android 11 / API 30). */
    @SuppressLint("MissingPermission")
    private fun startGnssTracking() {
        val lm = locationManager ?: return
        if (!hasFineLocationPermission()) return
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                _satellitesInView.value = status.satelliteCount
                _satellitesUsedInFix.value = used
            }

            override fun onStarted() {
                _gnssTracking.value = true
            }

            override fun onStopped() {
                _gnssTracking.value = false
                _satellitesInView.value = 0
                _satellitesUsedInFix.value = 0
            }
        }
        gnssCallback = callback
        val registered = runCatching { lm.registerGnssStatusCallback(callback) }.isSuccess
        _gnssTracking.value = registered
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        gnssCallback?.let {
            runCatching { locationManager?.unregisterGnssStatusCallback(it) }
        }
        super.onCleared()
    }
}
