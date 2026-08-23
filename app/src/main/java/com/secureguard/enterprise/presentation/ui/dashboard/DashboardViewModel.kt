package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class DashboardUiState(
    val totalAssets: Int = 0,
    val onlineAssets: Int = 0,
    val offlineAssets: Int = 0,
    val maintenanceAssets: Int = 0,
    val activeSearches: Int = 0,
    val detectionCount: Int = 0,
    val alertCount: Int = 0,
    val agentRunning: Boolean = false,
    val batteryLevel: Int = -1,
    val lastSyncTime: String = "--:--"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: SecureGuardRepository,
    private val agentService: AgentService
) : ViewModel() {

    /** Echter Geräte-Akkustand (BatteryManager, -1 = unbekannt). */
    private val battery = MutableStateFlow(readBatteryLevel())

    private val lastSync = MutableStateFlow("--:--")

    /** Anzahl gespeicherter Detektionen (aus der Room-Datenbank). */
    private val detectionCount: StateFlow<Int> = repository.getAllDetections()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Live-Werte gebündelt (Batterie, Detektionen, Sync-Zeit). */
    private val liveInfo: StateFlow<Triple<Int, Int, String>> = combine(
        battery,
        detectionCount,
        lastSync
    ) { b, d, s -> Triple(b, d, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(-1, 0, "--:--"))

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getWhitelistedAssets(),
        repository.getUnacknowledgedAlertCount(),
        agentService.agentStatus,
        liveInfo
    ) { assets, alertCount, agentStatus, info ->
        DashboardUiState(
            totalAssets = assets.size,
            onlineAssets = assets.count { it.status == AssetStatus.ONLINE },
            offlineAssets = assets.count { it.status == AssetStatus.OFFLINE },
            maintenanceAssets = assets.count { it.status == AssetStatus.MAINTENANCE },
            activeSearches = assets.count { it.status == AssetStatus.SEARCHING },
            detectionCount = info.second,
            alertCount = alertCount,
            agentRunning = agentStatus.running,
            batteryLevel = info.first,
            lastSyncTime = info.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agentRunning: StateFlow<Boolean> = agentService.agentStatus
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refresh()
        startAgent()
    }

    /** Liest alle Live-Werte neu (Batterie, Detektionen, Zeitstempel). */
    fun refresh() {
        battery.value = readBatteryLevel()
        lastSync.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
            // Vordergrunddienst beenden (Agent läuft über WorkManager weiter).
            appContext.stopService(
                android.content.Intent(appContext, com.secureguard.enterprise.services.AgentForegroundService::class.java)
            )
            setAutoStart(false)
        } else {
            startAgent()
        }
    }

    private fun startAgent() {
        agentService.start(
            AgentSettings(
                interval = 30,
                dynamicPriority = true,
                learningMode = true,
                offlineOnly = true,
                externalSources = false
            )
        )
        // Als Vordergrunddienst betreiben: schützt die Echtzeitsuche vor
        // Doze/Kill; nach Reboot nimmt BootCompletedReceiver sie wieder auf.
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                appContext,
                android.content.Intent(
                    appContext,
                    com.secureguard.enterprise.services.AgentForegroundService::class.java
                )
            )
        }
        setAutoStart(true)
        refresh()
    }

    /** Persistiert, ob der Agent nach Neustart automatisch wieder anlaufen soll. */
    private fun setAutoStart(enabled: Boolean) {
        appContext
            .getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(
                com.secureguard.enterprise.receiver.BootCompletedReceiver.KEY_AGENT_AUTOSTART,
                enabled
            )
            .apply()
    }

    /**
     * Echter Akkustand: BATTERY_PROPERTY_CAPACITY liefert Prozent; Fallback
     * über den Sticky-Broadcast ACTION_BATTERY_CHANGED.
     */
    private fun readBatteryLevel(): Int {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (capacity >= 0) return capacity
        val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) return (level * 100) / scale
        }
        return -1
    }
}
