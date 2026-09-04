package com.secureguard.enterprise.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Zustand der App-Hülle (Bottom-Navigation).
 *
 * Bewusst minimal gehalten: die Navigationsleiste ist auf jedem Top-Level-Screen
 * sichtbar, deshalb darf sie keine teuren Abfragen auslösen. Aktuell wird nur
 * die Zahl der offenen Alarme beobachtet – sie speist das Badge, damit ein
 * Alarm auch dann auffällt, wenn der Nutzer gerade nicht auf dem Dashboard ist.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    repository: SecureGuardRepository
) : ViewModel() {

    /** Anzahl nicht quittierter Alarme; bei Fehlern bewusst 0 statt Absturz. */
    val unacknowledgedAlerts: StateFlow<Int> =
        repository.getUnacknowledgedAlertCount()
            .catch { emit(0) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
