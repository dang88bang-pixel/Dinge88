package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.data.repository.SettingsRepository
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
    val alertCount: Int = 0,
    val agentRunning: Boolean = false,
    val batteryLevel: Int = 0,
    val lastSyncTime: String = "--:--"
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val battery = MutableStateFlow(readBattery())
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
        initialValue = DashboardUiState(batteryLevel = readBattery())
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
        battery.value = readBattery()
        lastSync.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
        } else {
            startAgent()
        }
    }

    private fun startAgent() {
        val s = settingsRepository.current
        agentService.start(
            AgentSettings(
                interval = s.agentIntervalSec.coerceAtLeast(5),
                dynamicPriority = s.dynamicPriority,
                learningMode = s.learningMode,
                offlineOnly = s.offlineOnly,
                externalSources = s.externalCrowdAllowed && s.consentGiven
            )
        )
        refresh()
    }

    private fun readBattery(): Int {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        return level.coerceIn(0, 100)
    }
}
