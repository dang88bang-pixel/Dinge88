package com.secureguard.enterprise.presentation.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val auditLogService: AuditLogService
) : ViewModel() {

    private val _auditEntries = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditEntries: StateFlow<List<AuditLog>> = _auditEntries.asStateFlow()

    val pinConfigured: StateFlow<Boolean> = MutableStateFlow(authManager.isPinConfigured()).also {
        viewModelScope.launch { it.value = authManager.isPinConfigured() }
    }

    val authState = authManager.state

    fun loadAuditLog() {
        viewModelScope.launch {
            _auditEntries.value = auditLogService.latest(100)
        }
    }

    fun configurePin(pin: String) {
        authManager.configurePin(pin)
        viewModelScope.launch {
            auditLogService.log("PIN_CHANGE", "PIN geändert über Security-Center")
            loadAuditLog()
        }
    }

    fun disablePin() {
        authManager.disablePin()
        viewModelScope.launch {
            auditLogService.log("PIN_DISABLE", "PIN entfernt über Security-Center")
            loadAuditLog()
        }
    }

    fun clearAuditLog() {
        viewModelScope.launch {
            auditLogService.log("AUDIT_CLEAR", "Audit-Log geleert")
            _auditEntries.value = emptyList()
        }
    }
}
