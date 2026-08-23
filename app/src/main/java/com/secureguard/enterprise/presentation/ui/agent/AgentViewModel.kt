package com.secureguard.enterprise.presentation.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val agentService: AgentService
) : ViewModel() {

    private val config = MutableStateFlow(
        AgentUiState(
            interval = agentService.agentStatus.value.settings.interval,
            dynamicPriority = agentService.agentStatus.value.settings.dynamicPriority,
            learningMode = agentService.agentStatus.value.settings.learningMode
        )
    )

    /** Ticker (1 s), damit Laufzeit/Fortschritt live mitlaufen. */
    private val ticker = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            while (true) {
                ticker.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }

    val uiState: StateFlow<AgentUiState> = combine(
        agentService.agentStatus,
        config,
        ticker
    ) { status, cfg, now ->
        val durationHours = durationHoursFor(cfg)
        cfg.copy(
            agentRunning = status.running,
            runtime = formatUptime(status.uptimeMillis),
            progress = realProgress(status, durationHours, now)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())

    /**
     * Echter Fortschritt:
     * - mit geplanter Gesamtdauer: verstrichene / geplante Laufzeit (0..1),
     * - unbegrenzt: Fortschritt innerhalb des aktuellen Abfragezyklus'
     *   (letzte Ausführung → nächste geplante Ausführung).
     */
    private fun realProgress(
        status: com.secureguard.enterprise.services.AgentStatus,
        durationHours: Int,
        now: Long
    ): Float {
        if (!status.running) return 0f
        if (durationHours > 0) {
            val totalMs = durationHours * 3_600_000L
            return (status.uptimeMillis.toFloat() / totalMs).coerceIn(0f, 1f)
        }
        val last = status.lastRunAt ?: return 0f
        val next = status.nextRunAt ?: return 0f
        if (next <= last) return 0f
        return ((now - last).toFloat() / (next - last)).coerceIn(0f, 1f)
    }

    /** Gesamtdauer in Stunden (0 = unbegrenzt) aus der UI-Konfiguration. */
    private fun durationHoursFor(cfg: AgentUiState): Int = when (cfg.duration) {
        "1h" -> 1
        "6h" -> 6
        "24h" -> 24
        "1w" -> 168
        "custom" -> cfg.customDays * 24
        else -> 0
    }

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
        val settings = AgentSettings(
            interval = state.interval.coerceAtLeast(5),
            dynamicPriority = state.dynamicPriority,
            learningMode = state.learningMode,
            offlineOnly = true,
            externalSources = false,
            durationHours = durationHoursFor(state)
        )
        agentService.start(settings)
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
