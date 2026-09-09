package com.secureguard.enterprise.presentation.ui.ports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.AutomaticPortSnapshot
import com.secureguard.enterprise.services.AutomaticPortView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel der automatischen Port-Ansicht: startet den Auto-Refresh des
 * nativen [AutomaticPortView]-Bridges beim Öffnen und stoppt ihn beim
 * Verlassen – die Ansicht bleibt damit ohne manuellen Aufwand aktuell.
 */
@HiltViewModel
class AutomaticPortViewViewModel @Inject constructor(
    private val automaticPortView: AutomaticPortView
) : ViewModel() {

    val snapshot: StateFlow<AutomaticPortSnapshot> = automaticPortView.snapshot

    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    init {
        automaticPortView.startAutoRefresh()
    }

    /** Manuelle Sofort-Prüfung (zusätzlich zum Auto-Modus). */
    fun probeNow() {
        viewModelScope.launch { automaticPortView.probeNow() }
    }

    fun setAutoRefresh(enabled: Boolean) {
        _autoRefreshEnabled.value = enabled
        if (enabled) {
            automaticPortView.startAutoRefresh()
        } else {
            automaticPortView.stopAutoRefresh()
        }
    }

    override fun onCleared() {
        automaticPortView.stopAutoRefresh()
        super.onCleared()
    }
}
