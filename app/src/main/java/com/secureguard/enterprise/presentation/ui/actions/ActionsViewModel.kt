package com.secureguard.enterprise.presentation.ui.actions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.PendingAction
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ACTIONS_CATALOG
import com.secureguard.enterprise.presentation.ui.common.ActionCategory
import com.secureguard.enterprise.presentation.ui.common.ActionDefinition
import com.secureguard.enterprise.presentation.ui.common.ActionKind
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.SatelliteService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ActionHistoryEntry(
    val label: String,
    val at: String,
    val success: Boolean,
    val detail: String
)

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val mqttService: MqttService,
    private val satelliteService: SatelliteService,
    private val roleManager: RoleManager,
    private val auditLogService: AuditLogService
) : ViewModel() {

    val assets: StateFlow<List<Asset>> = repository.getWhitelistedAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingActions: StateFlow<List<PendingAction>> = repository.getPendingActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _category = MutableStateFlow<ActionCategory?>(null)
    val category: StateFlow<ActionCategory?> = _category.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<ActionHistoryEntry>>(emptyList())
    val history: StateFlow<List<ActionHistoryEntry>> = _history.asStateFlow()

    private val _executing = MutableStateFlow(false)
    val executing: StateFlow<Boolean> = _executing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setSearch(q: String) { _search.value = q }
    fun setCategory(c: ActionCategory?) { _category.value = c }
    fun toggleFavorite(key: String) {
        _favorites.value = if (key in _favorites.value) _favorites.value - key else _favorites.value + key
    }
    fun toggleSelected(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) _selectedIds.value - id else _selectedIds.value + id
    }
    fun clearSelection() { _selectedIds.value = emptySet() }
    fun clearMessage() { _message.value = null }

    /** Gefilterter Katalog (Suche + Kategorie + Favoriten). */
    fun filteredCatalog(favoritesOnly: Boolean): List<ActionDefinition> {
        val q = _search.value.trim().lowercase()
        return ACTIONS_CATALOG.filter { def ->
            val matchesFav = !favoritesOnly || def.key in _favorites.value
            val matchesCat = _category.value == null || def.category == _category.value
            val matchesSearch = q.isEmpty() ||
                def.label.lowercase().contains(q) ||
                def.category.label.lowercase().contains(q) ||
                def.risk.label.lowercase().contains(q)
            matchesFav && matchesCat && matchesSearch
        }
    }

    /**
     * Führt eine Katalog-Aktion aus (§7/§8): kritische Aktionen werden in der
     * UI zuvor bestätigt (Bestätigung erfolgt über den SgConfirmDialog im
     * Screen). Device-Aktionen gehen 1:1 über [AgentService.sendAction]; bei
     * fehlender Zustellung greift die Offline-Queue (§36).
     */
    fun execute(def: ActionDefinition) {
        viewModelScope.launch {
            _executing.value = true
            val ok = when (def.kind) {
                ActionKind.SCENE -> {
                    // Reine 3D-Szenenaktion: löst NIE ein Gerätekommando aus.
                    auditLogService.log("SCENE_ACTION", "3D-Szene: ${def.key}")
                    _message.value = "3D-Szenenaktion ausgeführt (nur Darstellung)"
                    true
                }
                ActionKind.SERVICE -> runService(def)
                ActionKind.DEVICE -> runDevice(def)
            }
            _history.value = (_history.value + ActionHistoryEntry(
                label = def.label,
                at = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                success = ok,
                detail = _message.value.orEmpty()
            )).takeLast(60)
            _executing.value = false
        }
    }

    private suspend fun runDevice(def: ActionDefinition): Boolean {
        if (!roleManager.require(Permission.EXECUTE_ACTIONS)) {
            _message.value = "Keine Berechtigung (Rolle ${roleManager.currentRole})"
            return false
        }
        val wire = def.wireCommand
        if (wire == null) {
            _message.value = "Kein Gerätekommando hinterlegt"
            return false
        }
        val targets = assets.value.filter { it.id in _selectedIds.value }
        if (targets.isEmpty()) {
            _message.value = "Kein Asset ausgewählt"
            return false
        }
        var delivered = false
        targets.forEach { asset ->
            if (agentService.sendAction(asset, wire.wireCommand)) delivered = true
        }
        _message.value = if (delivered) {
            "${def.label}: an ${targets.size} Asset(s) gesendet"
        } else {
            "Kein Kanal erreichbar – ${def.label} in Offline-Queue"
        }
        auditLogService.log("ACTION", "${def.key} → ${targets.map { it.shortName }} (delivered=$delivered)")
        return delivered
    }

    private suspend fun runService(def: ActionDefinition): Boolean = when (def.key) {
        "AGENT_START" -> runCatching { agentService.start(); true }.getOrDefault(false)
        "AGENT_STOP" -> { agentService.stop(); true }
        "AGENT_CYCLE" -> runCatching {
            val r = agentService.runCycle()
            _message.value = "Zyklus: ${r.assetsChecked} Assets, ${r.detections} Treffer"
            true
        }.getOrDefault(false)
        "QUEUE_FLUSH" -> {
            val n = agentService.flushOfflineQueue()
            _message.value = "$n Aktionen aus der Offline-Queue zugestellt"
            true
        }
        "MQTT_RECONNECT" -> runCatching { mqttService.reconnect(); true }.getOrDefault(false)
        "GPS_QUERY" -> runCatching {
            val loc = satelliteService.currentLocation()
            _message.value = loc?.let {
                "GPS: ${"%.4f".format(it.latitude)}, ${"%.4f".format(it.longitude)}"
            } ?: "GPS nicht verfügbar"
            true
        }.getOrDefault(false)
        else -> {
            _message.value = "Aktion '${def.key}' im zugehörigen Screen verfügbar"
            false
        }
    }

    /** Offline-Queue-Retry über den bestehenden Kanal (§36). */
    fun retryQueue() {
        viewModelScope.launch {
            val replaced = agentService.flushOfflineQueue()
            _message.value = "$replaced Aktionen erneut zugestellt"
        }
    }

    fun removeQueueEntry(id: Long) {
        viewModelScope.launch { repository.removePendingAction(id) }
    }
}
