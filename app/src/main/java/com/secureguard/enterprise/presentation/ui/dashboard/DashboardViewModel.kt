package com.secureguard.enterprise.presentation.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettingsStore
import com.secureguard.enterprise.services.MqttService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ChannelActivity(val source: DetectionSource, val label: String, val count: Int)

data class DashboardUiState(
    val totalAssets: Int = 0,
    val onlineAssets: Int = 0,
    val offlineAssets: Int = 0,
    val maintenanceAssets: Int = 0,
    val searchingAssets: Int = 0,
    val alertCount: Int = 0,
    val detectionCount: Int = 0,
    val nodeCount: Int = 0,
    val agentRunning: Boolean = false,
    val mqttConnected: Boolean = false,
    val lastSyncTime: String = "--:--",
    val detectionTrend: List<Float> = emptyList(),
    val onlineTrend: List<Float> = emptyList(),
    val channelActivity: List<ChannelActivity> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val agentSettingsStore: AgentSettingsStore,
    private val apiNodeManager: ApiNodeManager,
    private val mqttService: MqttService,
    private val databaseCleanup: com.secureguard.enterprise.services.DatabaseCleanup,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val lastSync = MutableStateFlow("--:--")
    private val openAlarms = MutableStateFlow(0)

    // Sparkline-Histories (gleitende Ringpuffer).
    private val detectionHistory = MutableStateFlow<List<Float>>(emptyList())
    private val onlineHistory = MutableStateFlow<List<Float>>(emptyList())

    private val channelActivity = MutableStateFlow<List<ChannelActivity>>(emptyList())

    init {
        startAgent()
        viewModelScope.launch {
            // Detection-Trend.
            repository.getAllDetections().collect { detections ->
                val total = detections.size
                val trend = (detectionHistory.value + total.toFloat()).takeLast(24)
                detectionHistory.value = trend
                // Kanalaktivität (12 Kanäle kategorisiert).
                channelActivity.value = buildChannelActivity(detections)
            }
        }
        viewModelScope.launch {
            repository.getWhitelistedAssets().collect { assets ->
                val online = assets.count { it.status == AssetStatus.ONLINE }
                onlineHistory.value = (onlineHistory.value + online.toFloat()).takeLast(24)
                openAlarms.value = 0
            }
        }
        viewModelScope.launch {
            repository.getUnacknowledgedAlertCount().collect { count ->
                openAlarms.value = count
            }
        }
    }

    private fun buildChannelActivity(detections: List<Detection>): List<ChannelActivity> {
        return DetectionSource.entries
            .map { src ->
                ChannelActivity(
                    source = src,
                    label = src.name,
                    count = detections.count { it.sourceType == src }
                )
            }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getWhitelistedAssets(),
        openAlarms,
        agentService.agentStatus,
        apiNodeManager.nodeStatus,
        lastSync,
        detectionHistory,
        onlineHistory,
        channelActivity
    ) { assets, alerts, agent, nodes, sync, detTrend, onTrend, channels ->
        DashboardUiState(
            totalAssets = assets.size,
            onlineAssets = assets.count { it.status == AssetStatus.ONLINE },
            offlineAssets = assets.count { it.status == AssetStatus.OFFLINE },
            maintenanceAssets = assets.count { it.status == AssetStatus.MAINTENANCE },
            searchingAssets = assets.count { it.status == AssetStatus.SEARCHING },
            alertCount = alerts,
            detectionCount = detTrend.lastOrNull()?.toInt() ?: 0,
            nodeCount = nodes.size,
            agentRunning = agent.running,
            mqttConnected = mqttService.isConnected,
            lastSyncTime = sync,
            detectionTrend = detTrend,
            onlineTrend = onTrend,
            channelActivity = channels
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    val assets = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentAlerts = repository.getAlerts()
        .map { it.sortedByDescending { a -> a.timestamp }.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentDetections = repository.getAllDetections()
        .map { it.sortedByDescending { d -> d.timestamp }.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val agentRunning: StateFlow<Boolean> = agentService.agentStatus
        .map { it.running }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun refresh() {
        lastSync.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) {
            agentService.stop()
            context.stopService(android.content.Intent(context, AgentForegroundService::class.java))
        } else {
            startAgent()
        }
    }

    /** Schnellaktion: Offline-Queue über den bestehenden Kanal zustellen. */
    fun flushQueue() {
        viewModelScope.launch { agentService.flushOfflineQueue() }
    }

    /** Schnellaktion: manueller Suchzyklus. */
    fun runCycle() {
        viewModelScope.launch { agentService.runCycle() }
    }

    fun queryNodes() {
        viewModelScope.launch { apiNodeManager.refreshHealth() }
    }

    private fun startAgent() {
        agentService.start(agentSettingsStore.load())
        refresh()
    }
}
