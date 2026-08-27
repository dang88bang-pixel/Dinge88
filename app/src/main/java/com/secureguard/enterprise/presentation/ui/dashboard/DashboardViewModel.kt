package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import com.secureguard.enterprise.services.AgentSettingsStore
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
    val batteryLevel: Int = 0,
    val lastSyncTime: String = "--:--",
    val detectionCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val databaseCleanup: com.secureguard.enterprise.services.DatabaseCleanup,
    private val agentSettingsStore: com.secureguard.enterprise.services.AgentSettingsStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val lastSync = MutableStateFlow("--:--")
    private val _detectionCount = MutableStateFlow(0)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getWhitelistedAssets(),
        repository.getUnacknowledgedAlertCount(),
        agentService.agentStatus,
        lastSync,
        _detectionCount
    ) { assets, alertCount, agentStatus, sync, detCount ->
        DashboardUiState(
            totalAssets = assets.size,
            onlineAssets = assets.count { it.status == AssetStatus.ONLINE },
            offlineAssets = assets.count { it.status == AssetStatus.OFFLINE },
            maintenanceAssets = assets.count { it.status == AssetStatus.MAINTENANCE },
            activeSearches = assets.count { it.status == AssetStatus.SEARCHING },
            alertCount = alertCount,
            agentRunning = agentStatus.running,
            lastSyncTime = sync,
            detectionCount = detCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val detections = repository.getAllDetections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val alerts = repository.getAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agentRunning: StateFlow<Boolean> = agentService.agentStatus
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        startAgent()
        viewModelScope.launch {
            _detectionCount.value = databaseCleanup.detectionCount()
        }
    }

    fun refresh() {
        lastSync.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
            // Auch den Foreground-Service beenden – sonst bleibt die
            // persistente Status-Benachrichtigung stehen.
            context.stopService(
                android.content.Intent(context, AgentForegroundService::class.java)
            )
        } else {
            startAgent()
        }
    }

    private fun startAgent() {
        // Persistierte Einstellungen (Agent-Config-Screen) statt harter Defaults.
        agentService.start(agentSettingsStore.load())
        refresh()
    }
}
