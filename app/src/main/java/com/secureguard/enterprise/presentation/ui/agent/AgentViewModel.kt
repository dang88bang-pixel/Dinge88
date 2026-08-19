package com.secureguard.enterprise.presentation.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AgentSettings
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentService: AgentService
) : ViewModel() {

    private val _settings = MutableStateFlow(AgentSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow(AgentStatus(false, 0, 0L))
    val status: StateFlow<AgentStatus> = _status.asStateFlow()

    init {
        viewModelScope.launch {
            agentService.agentStatus.collect { _status.value = it }
        }
    }

    fun startAgent() {
        agentService.start(_settings.value)
    }

    fun stopAgent() {
        agentService.stop()
    }

    fun updateInterval(interval: Int) {
        _settings.value = _settings.value.copy(interval = interval)
    }

    fun updateDynamicPriority(value: Boolean) {
        _settings.value = _settings.value.copy(dynamicPriority = value)
    }

    fun updateLearningMode(value: Boolean) {
        _settings.value = _settings.value.copy(learningMode = value)
    }

    fun updateOfflineOnly(value: Boolean) {
        _settings.value = _settings.value.copy(offlineOnly = value)
    }

    fun updateExternalSources(value: Boolean) {
        _settings.value = _settings.value.copy(externalSources = value)
    }
}
