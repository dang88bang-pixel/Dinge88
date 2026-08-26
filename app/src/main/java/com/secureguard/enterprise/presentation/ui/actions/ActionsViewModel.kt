package com.secureguard.enterprise.presentation.ui.actions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.R
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.Role
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.security.User
import com.secureguard.enterprise.services.AgentService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val repository: SecureGuardRepository,
    private val agentService: AgentService
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
            val asset = selectedAsset.value ?: return@launch

            // RBAC permission check
            val user = User(id = "local", name = "Admin", role = Role.ADMIN)
            if (!RoleManager.hasPermission(user, Permission.EXECUTE_ACTIONS)) {
                _commandLog.value = _commandLog.value + context.getString(R.string.log_no_permission)
                return@launch
            }

            _isExecuting.value = true
            val success = agentService.sendAction(asset, actionType.wireCommand)
            val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val mark = if (success) "✓" else "✗"
            _commandLog.value = _commandLog.value + context.getString(
                R.string.log_command,
                ts, actionType.label, asset.shortName, mark
            )
            _isExecuting.value = false
        }
    }

    fun clearLog() { _commandLog.value = emptyList() }
}
