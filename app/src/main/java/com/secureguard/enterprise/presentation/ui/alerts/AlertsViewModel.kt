package com.secureguard.enterprise.presentation.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getUnresolvedAlerts().collect { _alerts.value = it }
        }
    }

    fun acknowledge(alert: Alert) {
        viewModelScope.launch {
            val resolved = alert.copy(resolved = true, resolvedAt = java.util.Date())
            repository.updateAlert(resolved)
        }
    }
}
