package com.secureguard.enterprise.presentation.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: SecureGuardRepository
) : ViewModel() {

    val alerts: StateFlow<List<Alert>> = repository.getAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledgeAlert(id) }
    }

    fun acknowledgeAll() {
        viewModelScope.launch { repository.acknowledgeAllAlerts() }
    }
}
