package com.secureguard.enterprise.presentation.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.SettingsRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AgentUiState(
    val agentRunning: Boolean = false,
    val runtime: String = "0d 0h 0m",
    val progress: Float = 0f,
    val duration: String = "unlimited",
    val customDays: Int = 0,
    val interval: Int = 30,
    val customInterval: Int = 30,
    val priority: String = "high",
    val dynamicPriority: Boolean = true,
    val learningMode: Boolean = true
)

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentService: AgentService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val saved = settingsRepository.current
    private val config = MutableStateFlow(
        AgentUiState(
            interval = saved.agentIntervalSec,
            customInterval = saved.agentIntervalSec,
            duration = saved.agentDuration,
            customDays = saved.agentCustomDays,
            dynamicPriority = saved.dynamicPriority,
            learningMode = saved.learningMode
        )
    )

    val uiState: StateFlow<AgentUiState> = combine(
        agentService.agentStatus,
        config
    ) { status, cfg ->
        cfg.copy(
            agentRunning = status.running,
            runtime = formatUptime(status.uptimeMillis),
            progress = computeProgress(status.uptimeMillis, cfg.duration, cfg.customDays)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())

    fun setDuration(duration: String) = config.update { it.copy(duration = duration) }
    fun setCustomDays(days: Int) = config.update { it.copy(customDays = days) }
    fun applyCustomDuration() {
        if (config.value.customDays > 0) config.update { it.copy(duration = "custom") }
    }

    fun setInterval(interval: Int) {
        config.update { it.copy(interval = interval, customInterval = interval) }
    }
    fun setCustomInterval(interval: Int) = config.update { it.copy(customInterval = interval) }
    fun applyCustomInterval() {
        if (config.value.customInterval >= 5) config.update { it.copy(interval = it.customInterval) }
    }

    fun setPriority(priority: String) = config.update { it.copy(priority = priority) }
    fun setDynamicPriority(enabled: Boolean) = config.update { it.copy(dynamicPriority = enabled) }
    fun setLearningMode(enabled: Boolean) = config.update { it.copy(learningMode = enabled) }

    fun toggleAgent() {
        if (agentService.agentStatus.value.running) agentService.stop() else saveSettings()
    }

    fun saveSettings() {
        val state = config.value
        settingsRepository.update {
            it.copy(
                agentIntervalSec = state.interval.coerceAtLeast(5),
                agentDuration = state.duration,
                agentCustomDays = state.customDays,
                dynamicPriority = state.dynamicPriority,
                learningMode = state.learningMode
            )
        }
        val persisted = settingsRepository.current
        val settings = AgentSettings(
            interval = persisted.agentIntervalSec.coerceAtLeast(5),
            dynamicPriority = persisted.dynamicPriority,
            learningMode = persisted.learningMode,
            offlineOnly = persisted.offlineOnly,
            externalSources = persisted.externalCrowdAllowed && persisted.consentGiven
        )
        agentService.start(settings)
    }

    private fun computeProgress(uptimeMs: Long, duration: String, customDays: Int): Float {
        val total = durationMillis(duration, customDays) ?: return 0f
        if (total <= 0L || uptimeMs <= 0L) return 0f
        return ((uptimeMs.toDouble() / total) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    private fun durationMillis(duration: String, customDays: Int): Long? = when (duration) {
        "1h" -> 3_600_000L
        "6h" -> 21_600_000L
        "24h" -> 86_400_000L
        "1w" -> 604_800_000L
        "custom" -> customDays.coerceAtLeast(1) * 86_400_000L
        else -> null
    }

    private fun formatUptime(ms: Long): String {
        if (ms <= 0) return "0d 0h 0m"
        val totalMinutes = ms / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return "${days}d ${hours}h ${minutes}m"
    }
}
