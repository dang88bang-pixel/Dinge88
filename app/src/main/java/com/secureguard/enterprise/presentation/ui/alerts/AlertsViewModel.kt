package com.secureguard.enterprise.presentation.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AlertFilter(val label: String) {
    ALL("Alle"),
    ACTIVE("Aktiv"),
    ACKNOWLEDGED("Quittiert"),
    RESOLVED("Abgeschlossen"),
    CRITICAL("Kritisch")
}

data class AlertsUiState(
    val filter: AlertFilter = AlertFilter.ACTIVE,
    val hasError: Boolean = false
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    val alerts: StateFlow<List<Alert>> = repository.getAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openAlertCount: StateFlow<Int> = repository.getUnacknowledgedAlertCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Gefilterte Liste (Statusinformation immer mit Text/Icon, nie nur Farbe). */
    val visibleAlerts: StateFlow<List<Alert>> = combine(alerts, _uiState) { list, state ->
        filter(list, state.filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: AlertFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledgeAlert(id) }
    }

    fun acknowledgeAll() {
        viewModelScope.launch { repository.acknowledgeAllAlerts() }
    }

    fun resolve(id: Long) {
        viewModelScope.launch { repository.resolveAlert(id) }
    }

    fun deleteResolved() {
        viewModelScope.launch {
            repository.deleteResolvedAlerts()
            _loading.value = false
        }
    }

    /** Retry: Repository-Flow ist reaktiv – ein Refresh setzt Fehler zurück. */
    fun retry() {
        _uiState.value = _uiState.value.copy(hasError = false)
        _loading.value = true
        viewModelScope.launch {
            _loading.value = false
        }
    }

    private fun filter(list: List<Alert>, f: AlertFilter): List<Alert> = when (f) {
        AlertFilter.ALL -> list
        AlertFilter.ACTIVE -> list.filter { !it.acknowledged && !it.resolved }
        AlertFilter.ACKNOWLEDGED -> list.filter { it.acknowledged && !it.resolved }
        AlertFilter.RESOLVED -> list.filter { it.resolved }
        AlertFilter.CRITICAL -> list.filter { it.severity == AlertSeverity.CRITICAL && !it.resolved }
    }
}
