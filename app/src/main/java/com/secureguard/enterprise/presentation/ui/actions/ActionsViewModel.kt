package com.secureguard.enterprise.presentation.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.services.AgentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val authManager: AuthManager
) : ViewModel() {

    private val assetsFlow = repository.getWhitelistedAssets()

    val assets: StateFlow<List<Asset>> = assetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedId = MutableStateFlow<String?>(null)

    val selectedAsset: StateFlow<Asset?> = combine(assetsFlow, selectedId) { list, id ->
        if (id != null) list.firstOrNull { it.id == id } else list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _commandLog = MutableStateFlow<List<String>>(emptyList())
    val commandLog: StateFlow<List<String>> = _commandLog.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _menuExpanded = MutableStateFlow(false)
    val menuExpanded: StateFlow<Boolean> = _menuExpanded.asStateFlow()

    fun setMenuExpanded(expanded: Boolean) { _menuExpanded.value = expanded }

    fun selectAsset(asset: Asset) {
        selectedId.value = asset.id
        _menuExpanded.value = false
    }

    fun executeAction(actionType: ActionType) {
        viewModelScope.launch {
            // RBAC-Kette: Ausführung nur mit Berechtigung EXECUTE_ACTIONS.
            if (!authManager.hasPermission(Permission.EXECUTE_ACTIONS)) {
                val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _commandLog.value = _commandLog.value +
                    "$ts → ⛔ ${actionType.label}: keine Berechtigung (Rolle " +
                    "${authManager.currentUser.value.role.name})"
                return@launch
            }
            val asset = selectedAsset.value ?: return@launch
            _isExecuting.value = true
            // Volle Kanalkette: MQTT → WebSocket → BLE-GATT; ohne Zustellung
            // landet die Aktion in der Offline-Queue (siehe AgentService).
            val success = agentService.sendAction(asset, actionType.wireCommand)
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val mark = if (success) "✓" else "⏳ (offline eingereiht)"
            _commandLog.value = _commandLog.value +
                "$ts → ${actionType.label} an ${asset.shortName} $mark"
            _isExecuting.value = false
        }
    }

    fun clearLog() { _commandLog.value = emptyList() }
}
