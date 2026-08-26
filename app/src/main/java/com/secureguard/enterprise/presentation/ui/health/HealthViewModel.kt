package com.secureguard.enterprise.presentation.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.HealthMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val healthMonitorService: HealthMonitorService
) : ViewModel() {

    private val _health = MutableStateFlow<HealthMonitorService.SystemHealth?>(null)
    val health: StateFlow<HealthMonitorService.SystemHealth?> = _health.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _health.value = runCatching { healthMonitorService.snapshot() }.getOrNull()
            _loading.value = false
        }
    }
}
