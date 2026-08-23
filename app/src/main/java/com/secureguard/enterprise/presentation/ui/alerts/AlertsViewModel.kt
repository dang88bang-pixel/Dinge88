package com.secureguard.enterprise.presentation.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AlertSoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val alertSoundManager: AlertSoundManager
) : ViewModel() {

    val alerts: StateFlow<List<Alert>> = repository.getAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** IDs bereits bekannter Alarme – erkennt neu eingetroffene kritische Alarme. */
    private val knownIds = mutableSetOf<Long>()

    init {
        // Neue CRITICAL-/WARNING-Alarme akustisch melden (echter Ton je Stufe).
        viewModelScope.launch {
            var primed = false
            alerts.collect { list ->
                val fresh = list.filter { it.id !in knownIds }
                knownIds += list.map { it.id }
                // Beim ersten Laden nicht alle historischen Alarme abspielen.
                if (primed && fresh.isNotEmpty()) {
                    fresh.maxByOrNull { it.severity.ordinal }?.let { loudest ->
                        alertSoundManager.playForSeverity(loudest.severity.name)
                    }
                }
                primed = true
            }
        }
    }

    fun acknowledge(id: Long) {
        viewModelScope.launch { repository.acknowledgeAlert(id) }
    }

    fun acknowledgeAll() {
        viewModelScope.launch { repository.acknowledgeAllAlerts() }
    }

    /** Stoppt den Alarmton (Button in der UI). */
    fun stopAlertSound() {
        alertSoundManager.stop()
    }

    /** Testton je Schweregrad (Diagnose aus der UI). */
    fun testSound(severity: AlertSeverity) {
        alertSoundManager.playForSeverity(severity.name)
    }
}
