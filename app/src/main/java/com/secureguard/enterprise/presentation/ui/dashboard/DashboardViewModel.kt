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
import kotlinx.coroutines.launch
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
    val alertCount: Int = 0,
    val agentRunning: Boolean = false,
    /** Echter Akkustand (BatteryManager); `null`, wenn nicht auslesbar. */
    val batteryLevel: Int? = null,
    val lastSyncTime: String = "--:--"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: SecureGuardRepository,
    private val agentService: AgentService
) : ViewModel() {

    private val battery = MutableStateFlow(readRealBatteryLevel())
    private val lastSync = MutableStateFlow("--:--")

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getWhitelistedAssets(),
        repository.getUnacknowledgedAlertCount(),
        agentService.agentStatus,
        battery,
        lastSync
    ) { assets, alertCount, agentStatus, batteryLevel, sync ->
        DashboardUiState(
            totalAssets = assets.size,
            onlineAssets = assets.count { it.status == AssetStatus.ONLINE },
            offlineAssets = assets.count { it.status == AssetStatus.OFFLINE },
            maintenanceAssets = assets.count { it.status == AssetStatus.MAINTENANCE },
            activeSearches = assets.count { it.status == AssetStatus.SEARCHING },
            alertCount = alertCount,
            agentRunning = agentStatus.running,
            batteryLevel = batteryLevel,
            lastSyncTime = sync
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
        startAgent()
    }

    fun refresh() {
        battery.value = readRealBatteryLevel()
        lastSync.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
        } else {
            startAgent()
        }
    }

    /**
     * Liest den echten Geräte-Akkustand: primär über
     * [BatteryManager.BATTERY_PROPERTY_CAPACITY], alternativ über den
     * Sticky-Broadcast `ACTION_BATTERY_CHANGED`. `null`, wenn beides nicht
     * auslesbar ist (z. B. Desktop-Emulator ohne Batterie) – kein Fake-Wert.
     */
    private fun readRealBatteryLevel(): Int? {
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (capacity > 0) return capacity.coerceAtMost(100)

        val intent: Intent? =
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100 / scale).coerceIn(0, 100)
        } else null
    }
}
