package com.secureguard.enterprise.presentation.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AgentSettings
import com.secureguard.enterprise.data.repository.SettingsRepository
import com.secureguard.enterprise.services.AgentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentService: AgentService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentUiState())
    val uiState: StateFlow<AgentUiState> = _uiState.asStateFlow()

    init {
        monitorAgentStatus()
    }

    private fun monitorAgentStatus() {
        viewModelScope.launch {
            agentService.agentStatus.collect { status ->
                _uiState.update { state ->
                    state.copy(
                        agentRunning = status.running,
                        runtime = calculateRuntime(status.running),
                        progress = calculateProgress(status.running)
                    )
                }
            }
        }
    }

    /** Echte Laufzeit aus dem persistierten Startzeitpunkt. */
    private fun calculateRuntime(running: Boolean): String {
        val start = settingsRepository.agentStartTime.value
        if (!running || start <= 0L) return "0h 0m"
        val totalSec = (System.currentTimeMillis() - start) / 1000
        val d = totalSec / 86400
        val h = (totalSec % 86400) / 3600
        val m = (totalSec % 3600) / 60
        return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
    }

    /**
     * Fortschritt der eingestellten Gesamtdauer (0..100).
     * Bei unbegrenzter Dauer (0) gibt es kein Fortschrittsziel.
     */
    private fun calculateProgress(running: Boolean): Float {
        val start = settingsRepository.agentStartTime.value
        val durationSec = settingsRepository.agentDurationSeconds.value
        if (!running || start <= 0L || durationSec <= 0L) return 0f
        val elapsedSec = (System.currentTimeMillis() - start) / 1000
        return (elapsedSec.toFloat() / durationSec).coerceIn(0f, 1f) * 100f
    }

    fun setDuration(duration: String) {
        _uiState.update { state -> state.copy(duration = duration) }
        settingsRepository.setAgentDurationSeconds(
            when (duration) {
                "1h" -> 3_600L
                "6h" -> 21_600L
                "24h" -> 86_400L
                "1w" -> 604_800L
                else -> 0L // unbegrenzt
            }
        )
    }

    fun setCustomDays(days: Int) {
        _uiState.update { state -> state.copy(customDays = days) }
    }

    fun applyCustomDuration() {
        val days = _uiState.value.customDays
        if (days > 0) {
            _uiState.update { state -> state.copy(duration = "custom") }
            settingsRepository.setAgentDurationSeconds(days * 86_400L)
        }
    }

    fun setInterval(interval: Int) {
        _uiState.update { state -> state.copy(interval = interval) }
    }

    fun setCustomInterval(interval: Int) {
        _uiState.update { state -> state.copy(customInterval = interval) }
    }

    fun applyCustomInterval() {
        val interval = _uiState.value.customInterval
        if (interval > 0) {
            _uiState.update { state -> state.copy(interval = interval) }
        }
    }

    fun setPriority(priority: String) {
        _uiState.update { state -> state.copy(priority = priority) }
    }

    fun setDynamicPriority(enabled: Boolean) {
        _uiState.update { state -> state.copy(dynamicPriority = enabled) }
    }

    fun setLearningMode(enabled: Boolean) {
        _uiState.update { state -> state.copy(learningMode = enabled) }
    }

    fun saveSettings() {
        val state = _uiState.value
        val settings = AgentSettings(
            interval = state.interval,
            dynamicPriority = state.dynamicPriority,
            learningMode = state.learningMode,
            offlineOnly = true,
            externalSources = false
        )
        agentService.start(settings)
    }
}

data class AgentUiState(
    val agentRunning: Boolean = false,
    val runtime: String = "0h 0m",
    val progress: Float = 0f,
    val duration: String = "unlimited",
    val customDays: Int = 0,
    val interval: Int = 30,
    val customInterval: Int = 30,
    val priority: String = "high",
    val dynamicPriority: Boolean = true,
    val learningMode: Boolean = true
)
