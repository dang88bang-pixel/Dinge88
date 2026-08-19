package com.secureguard.enterprise.presentation.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AgentSettings
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
    private val agentService: AgentService
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
                        progress = 85f // Simuliert
                    )
                }
            }
        }
    }

    private fun calculateRuntime(running: Boolean): String {
        if (!running) return "0h 0m"
        // TODO: Echte Laufzeit aus Persistenz berechnen.
        return "12d 4h 32m"
    }

    fun setDuration(duration: String) {
        _uiState.update { state -> state.copy(duration = duration) }
    }

    fun setCustomDays(days: Int) {
        _uiState.update { state -> state.copy(customDays = days) }
    }

    fun applyCustomDuration() {
        val days = _uiState.value.customDays
        if (days > 0) {
            _uiState.update { state -> state.copy(duration = "custom") }
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
