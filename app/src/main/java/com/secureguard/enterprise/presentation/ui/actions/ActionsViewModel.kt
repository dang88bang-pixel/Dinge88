package com.secureguard.enterprise.presentation.ui.actions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.ui.common.ActionCatalog
import com.secureguard.enterprise.presentation.ui.common.ActionSpec
import com.secureguard.enterprise.presentation.ui.common.ActionType
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.OfflineQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Zustellergebnis eines abgesetzten Befehls. */
enum class CommandState(val label: String) {
    RUNNING("läuft"),
    DELIVERED("zugestellt"),
    QUEUED("in Warteschlange"),
    DENIED("abgelehnt"),
    BLOCKED("blockiert")
}

/** Ein strukturierter Eintrag im Befehls-Protokoll. */
data class CommandLogEntry(
    val id: Long,
    val timestampMillis: Long,
    val actionType: ActionType,
    val assetId: String,
    val assetName: String,
    val state: CommandState,
    val detail: String = ""
) {
    val time: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}

/** Filter für die Protokollansicht. */
enum class LogFilter(val label: String) {
    ALL("Alle"),
    DELIVERED("Zugestellt"),
    QUEUED("Warteschlange"),
    PROBLEM("Probleme")
}

/** Kurzfeedback für die Snackbar. */
data class ActionFeedback(
    val message: String,
    val isError: Boolean = false,
    val actionLabel: String? = null,
    val retryAction: ActionType? = null
)

data class ActionsUiState(
    val executing: Boolean = false,
    val runningAction: ActionType? = null,
    val canExecute: Boolean = true,
    val roleName: String = "",
    val pendingQueue: Int = 0,
    val deliveredCount: Int = 0,
    val failedCount: Int = 0
)

@HiltViewModel
class ActionsViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val roleManager: RoleManager,
    private val offlineQueue: OfflineQueue,
    @ApplicationContext context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val assetsFlow = repository.getWhitelistedAssets()

    val assets: StateFlow<List<Asset>> = assetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Ziel-Auswahl (Mehrfachauswahl) ----------------

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    private val _onlyOnline = MutableStateFlow(false)
    val onlyOnline: StateFlow<Boolean> = _onlyOnline.asStateFlow()

    /** Gefilterte Asset-Liste für die Zielauswahl. */
    val pickerAssets: StateFlow<List<Asset>> =
        combine(assetsFlow, _pickerQuery, _onlyOnline) { list, query, online ->
            val q = query.trim().lowercase()
            list.filter { asset ->
                val matches = q.isEmpty() ||
                    asset.name.lowercase().contains(q) ||
                    asset.shortName.lowercase().contains(q) ||
                    asset.mac.lowercase().contains(q)
                val matchesOnline = !online || asset.status == AssetStatus.ONLINE
                matches && matchesOnline
            }.sortedWith(compareBy({ it.status != AssetStatus.ONLINE }, { it.shortName }))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tatsächlich adressierte Assets (Auswahl ∩ vorhandene Assets). */
    val targets: StateFlow<List<Asset>> =
        combine(assetsFlow, _selectedIds) { list, ids ->
            when {
                ids.isEmpty() -> emptyList()
                else -> list.filter { it.id in ids }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Erstes Ziel – für Detailanzeigen (RSSI, Akku, Position). */
    val primaryTarget: StateFlow<Asset?> = targets
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------------- Favoriten ----------------

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<Set<ActionType>> = _favorites.asStateFlow()

    // ---------------- Protokoll ----------------

    private val _log = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    private val _logFilter = MutableStateFlow(LogFilter.ALL)
    val logFilter: StateFlow<LogFilter> = _logFilter.asStateFlow()

    val log: StateFlow<List<CommandLogEntry>> =
        combine(_log, _logFilter) { entries, filter ->
            when (filter) {
                LogFilter.ALL -> entries
                LogFilter.DELIVERED -> entries.filter { it.state == CommandState.DELIVERED }
                LogFilter.QUEUED -> entries.filter { it.state == CommandState.QUEUED }
                LogFilter.PROBLEM -> entries.filter {
                    it.state == CommandState.DENIED || it.state == CommandState.BLOCKED
                }
            }.asReversed()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Freitext / Ausführung ----------------

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()

    private val _runningAction = MutableStateFlow<ActionType?>(null)
    private val _lastAction = MutableStateFlow<ActionType?>(null)
    val lastAction: StateFlow<ActionType?> = _lastAction.asStateFlow()

    private val _feedback = MutableStateFlow<ActionFeedback?>(null)
    val feedback: StateFlow<ActionFeedback?> = _feedback.asStateFlow()

    private val pendingCount: StateFlow<Int> = offlineQueue.pending
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val uiState: StateFlow<ActionsUiState> = combine(
        _runningAction,
        _log,
        pendingCount,
        roleManager.role
    ) { running, entries, queue, role ->
        ActionsUiState(
            executing = running != null,
            runningAction = running,
            canExecute = roleManager.has(Permission.EXECUTE_ACTIONS),
            roleName = role.name,
            pendingQueue = queue,
            deliveredCount = entries.count { it.state == CommandState.DELIVERED },
            failedCount = entries.count {
                it.state == CommandState.DENIED || it.state == CommandState.BLOCKED
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActionsUiState())

    private var logId = 0L

    init {
        // Standardauswahl: erstes verfügbares Asset, sobald Daten da sind.
        viewModelScope.launch {
            assetsFlow.collect { list ->
                if (_selectedIds.value.isEmpty() && list.isNotEmpty()) {
                    _selectedIds.value = setOf(list.first().id)
                }
                // Entfernte Assets aus der Auswahl nehmen.
                val ids = list.map { it.id }.toSet()
                val cleaned = _selectedIds.value.filter { it in ids }.toSet()
                if (cleaned.size != _selectedIds.value.size) _selectedIds.value = cleaned
            }
        }
    }

    // ---------------- Auswahl-API ----------------

    fun setPickerQuery(query: String) { _pickerQuery.value = query }

    fun toggleOnlyOnline() { _onlyOnline.value = !_onlyOnline.value }

    fun toggleTarget(asset: Asset) {
        val current = _selectedIds.value
        _selectedIds.value =
            if (asset.id in current) current - asset.id else current + asset.id
    }

    fun selectSingle(asset: Asset) { _selectedIds.value = setOf(asset.id) }

    fun selectAllVisible() {
        _selectedIds.value = pickerAssets.value.map { it.id }.toSet()
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun setNote(text: String) { _note.value = text }

    // ---------------- Favoriten-API ----------------

    fun toggleFavorite(type: ActionType) {
        val current = _favorites.value
        val next = if (type in current) current - type else current + type
        _favorites.value = next
        prefs.edit().putStringSet(KEY_FAVORITES, next.map { it.name }.toSet()).apply()
    }

    // ---------------- Protokoll-API ----------------

    fun setLogFilter(filter: LogFilter) { _logFilter.value = filter }

    fun clearLog() { _log.value = emptyList() }

    /** Protokoll als Text – für Teilen / Zwischenablage. */
    fun logAsText(): String = _log.value.joinToString("\n") { entry ->
        "${entry.time}  ${entry.actionType.label} → ${entry.assetName}  " +
            "[${entry.state.label}]${if (entry.detail.isBlank()) "" else "  ${entry.detail}"}"
    }

    fun consumeFeedback() { _feedback.value = null }

    // ---------------- Ausführung ----------------

    /**
     * Führt [spec] für alle ausgewählten Ziele aus. Für jedes Ziel entsteht ein
     * eigener Protokolleintrag; nicht zustellbare Befehle landen (sofern
     * erlaubt) in der Offline-Queue.
     */
    fun execute(spec: ActionSpec) {
        val selected = targets.value
        if (selected.isEmpty()) {
            _feedback.value = ActionFeedback("Kein Ziel ausgewählt", isError = true)
            return
        }
        if (!roleManager.require(Permission.EXECUTE_ACTIONS)) {
            appendLog(
                spec.type,
                selected.first(),
                CommandState.DENIED,
                "Rolle ${roleManager.currentRole} ohne Recht EXECUTE_ACTIONS"
            )
            _feedback.value = ActionFeedback(
                "Rolle ${roleManager.currentRole} darf keine Aktionen senden",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            _runningAction.value = spec.type
            _lastAction.value = spec.type
            var delivered = 0
            var queued = 0
            var blocked = 0

            selected.forEach { asset ->
                if (spec.requiresOnline && asset.status != AssetStatus.ONLINE) {
                    blocked++
                    appendLog(
                        spec.type, asset, CommandState.BLOCKED,
                        "Asset ist ${asset.status.name.lowercase()} – Aktion verlangt Online-Status"
                    )
                    return@forEach
                }
                val command = buildCommand(spec)
                val ok = runCatching { agentService.sendAction(asset, command) }
                    .getOrElse { error ->
                        appendLog(
                            spec.type, asset, CommandState.BLOCKED,
                            error.message ?: error::class.java.simpleName
                        )
                        blocked++
                        return@forEach
                    }
                if (ok) {
                    delivered++
                    appendLog(spec.type, asset, CommandState.DELIVERED, "Direktkanal")
                } else {
                    queued++
                    appendLog(
                        spec.type, asset, CommandState.QUEUED,
                        if (spec.queueable) "Wird bei Verbindung erneut zugestellt"
                        else "Kein Kanal erreichbar"
                    )
                }
            }

            _runningAction.value = null
            _feedback.value = buildFeedback(spec, delivered, queued, blocked)
            if (spec.acceptsNote) _note.value = ""
        }
    }

    /** Offline-Queue manuell zustellen (z. B. nach WLAN-Wechsel). */
    fun flushQueue() {
        viewModelScope.launch {
            val sent = runCatching { agentService.flushOfflineQueue() }.getOrDefault(0)
            _feedback.value = when {
                sent > 0 -> ActionFeedback("$sent Befehl(e) aus der Warteschlange zugestellt")
                else -> ActionFeedback("Kein Befehl zustellbar – Broker nicht erreichbar", true)
            }
        }
    }

    /** Letzte Aktion für die aktuelle Auswahl wiederholen. */
    fun repeatLast() {
        val type = _lastAction.value ?: return
        execute(ActionCatalog.of(type))
    }

    private fun buildCommand(spec: ActionSpec): String {
        val text = _note.value.trim()
        return if (spec.acceptsNote && text.isNotEmpty()) {
            "${spec.type.wireCommand}:${text.take(120)}"
        } else {
            spec.type.wireCommand
        }
    }

    private fun buildFeedback(
        spec: ActionSpec,
        delivered: Int,
        queued: Int,
        blocked: Int
    ): ActionFeedback {
        val parts = buildList {
            if (delivered > 0) add("$delivered zugestellt")
            if (queued > 0) add("$queued in Warteschlange")
            if (blocked > 0) add("$blocked blockiert")
        }
        val summary = if (parts.isEmpty()) "Keine Zustellung" else parts.joinToString(" · ")
        return ActionFeedback(
            message = "${spec.title}: $summary",
            isError = delivered == 0,
            actionLabel = if (delivered == 0) "Erneut" else null,
            retryAction = if (delivered == 0) spec.type else null
        )
    }

    private fun appendLog(
        type: ActionType,
        asset: Asset,
        state: CommandState,
        detail: String
    ) {
        val entry = CommandLogEntry(
            id = logId++,
            timestampMillis = System.currentTimeMillis(),
            actionType = type,
            assetId = asset.id,
            assetName = asset.shortName,
            state = state,
            detail = detail
        )
        _log.value = (_log.value + entry).takeLast(MAX_LOG)
    }

    private fun loadFavorites(): Set<ActionType> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
            .mapNotNull { name -> runCatching { ActionType.valueOf(name) }.getOrNull() }
            .toSet()

    private companion object {
        const val PREFS = "secureguard_actions"
        const val KEY_FAVORITES = "favorite_actions"
        const val MAX_LOG = 200
    }
}
