package com.secureguard.enterprise.presentation.ui.opscenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.PendingAction
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentStatus
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.SatelliteService
import com.secureguard.enterprise.services.WebSocketService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Snapshot für die 3D-Operations-Center-Oberfläche (§28).
 *
 * Enthält Assets, Alarme, Detections, Agent-Status und Offline-Queue.
 * Als `@Volatile`-Feld gehalten (thread-safe Sichtbarkeit über Threads), der
 * für Compose relevante Teil zusätzlich als `StateFlow`.
 */
data class OpsSnapshot(
    val assets: List<Asset> = emptyList(),
    val alarms: List<Alert> = emptyList(),
    val detections: List<Detection> = emptyList(),
    val pendingActions: List<PendingAction> = emptyList(),
    val agent: AgentStatus = AgentStatus(),
    val nodeCount: Int = 0,
    val mqttConnected: Boolean = false,
    val websocketConfigured: Boolean = false,
    val takenAt: Long = System.currentTimeMillis()
)

/**
 * ViewModel des 3D Operations Center.
 *
 * Einzige Business-Logik: bestehende Services/Repositories als Snapshot für
 * die WebView-Präsentation bereitstellen und Aktionen über die vorhandenen
 * Kanäle ([AgentService.sendAction], Offline-Queue via [AgentService.flushOfflineQueue])
 * dispatch-en. Keine parallele/konkurrierende Fachlogik (§44).
 */
@HiltViewModel
class OpsCenterViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val apiNodeManager: ApiNodeManager,
    private val roleManager: RoleManager,
    private val auditLogService: AuditLogService,
    private val satelliteService: SatelliteService
) : ViewModel() {

    private val gson = Gson()

    private val assetsFlow = repository.getWhitelistedAssets()
    private val detectionsFlow = repository.getAllDetections()
    private val alarmsFlow = repository.getAlerts()
    private val queueFlow = repository.getPendingActions()

    val assets: StateFlow<List<Asset>> = assetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val detections: StateFlow<List<Detection>> = detectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val alarms: StateFlow<List<Alert>> = alarmsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingActions: StateFlow<List<PendingAction>> = queueFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val openAlarmCount: StateFlow<Int> = repository.getUnacknowledgedAlertCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val agentStatus: StateFlow<AgentStatus> = agentService.agentStatus
    val nodeCount: StateFlow<Int> = apiNodeManager.nodeStatus
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Thread-sicherer Gesamt-Snapshot (§28, `@Volatile`). */
    @Volatile
    var snapshot: OpsSnapshot = OpsSnapshot()
        private set

    private val _executing = MutableStateFlow(false)
    val executing: StateFlow<Boolean> = _executing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _history = MutableStateFlow<List<OpsHistoryEntry>>(emptyList())
    val history: StateFlow<List<OpsHistoryEntry>> = _history.asStateFlow()

    private val favorites = MutableStateFlow<Set<String>>(emptySet())
    val favoriteKeys: StateFlow<Set<String>> = favorites.asStateFlow()

    /** Outbound UI-Events an die WebView (z. B. Queue-Status). */
    private val _webEvents = MutableStateFlow<String?>(null)
    val webEvents: StateFlow<String?> = _webEvents.asStateFlow()

    private data class FiveTuple(
        val assets: List<Asset>,
        val alarms: List<Alert>,
        val detections: List<Detection>,
        val queue: List<PendingAction>,
        val agent: AgentStatus
    )

    init {
        viewModelScope.launch {
            // Auf echte Daten lauschen und den Snapshot fortlaufend aktualisieren.
            // combine() kann max. 5 Flows → in zwei Stufen zusammenführen.
            kotlinx.coroutines.flow.combine(
                kotlinx.coroutines.flow.combine(
                    assetsFlow, alarmsFlow, detectionsFlow, queueFlow, agentService.agentStatus
                ) { a, al, d, q, agent ->
                    FiveTuple(a, al, d, q, agent)
                },
                apiNodeManager.nodeStatus
            ) { base, nodes ->
                buildSnapshot(base.assets, base.alarms, base.detections, base.queue, base.agent, nodes.size)
            }.collect { snap ->
                snapshot = snap
            }
        }
    }

    private fun buildSnapshot(
        assets: List<Asset>,
        alarms: List<Alert>,
        detections: List<Detection>,
        queue: List<PendingAction>,
        agent: AgentStatus,
        nodes: Int
    ) = OpsSnapshot(
        assets = assets,
        alarms = alarms,
        detections = detections,
        pendingActions = queue,
        agent = agent,
        nodeCount = nodes,
        mqttConnected = mqttService.isConnected,
        websocketConfigured = webSocketService.isConfigured,
        takenAt = System.currentTimeMillis()
    )

    fun refresh() {
        viewModelScope.launch {
            buildCurrentSnapshot()
        }
    }

    private suspend fun buildCurrentSnapshot() {
        val assets = assetsFlow.first()
        val alarms = alarmsFlow.first()
        val detections = detectionsFlow.first()
        val queue = queueFlow.first()
        applySnapshot(
            buildSnapshot(
                assets, alarms, detections, queue,
                agentService.agentStatus.value, apiNodeManager.nodeStatus.value.size
            )
        )
    }

    private fun applySnapshot(snap: OpsSnapshot) {
        snapshot = snap
        _lastError.value = null
    }

    /** JSON-Snapshot für die WebView-Bridge (`window.SecureGuardOps.ingest`). */
    fun snapshotJson(): String = gson.toJson(toJsMap(snapshot))

    fun toggleFavorite(key: String) {
        val cur = favorites.value
        favorites.value = if (key in cur) cur - key else cur + key
    }

    /** Führt eine Aktion aus dem Katalog aus (Device/Service/Scene). */
    fun execute(key: String) {
        val definition = com.secureguard.enterprise.presentation.ui.common.ACTIONS_CATALOG
            .firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            _executing.value = true
            val ok = when {
                definition.isScene -> {
                    // Reine 3D-Szenenaktion: nur an die WebView weiterreichen,
                    // niemals ein Gerätekommando auslösen (§8).
                    auditLogService.log("SCENE_ACTION", "3D-Szene: ${definition.key}")
                    true
                }
                definition.kind == com.secureguard.enterprise.presentation.ui.common.ActionKind.SERVICE ->
                    executeService(definition.key)
                else -> executeDevice(definition)
            }
            _history.value = (_history.value + OpsHistoryEntry(
                key = definition.key,
                label = definition.label,
                risk = definition.risk,
                at = System.currentTimeMillis(),
                success = ok
            )).takeLast(60)
            _executing.value = false
        }
    }

    /** Die acht Geräteaktionen laufen 1:1 über [AgentService.sendAction]. */
    private suspend fun executeDevice(definition: com.secureguard.enterprise.presentation.ui.common.ActionDefinition): Boolean {
        if (!roleManager.require(Permission.EXECUTE_ACTIONS)) {
            _lastError.value = "Keine Berechtigung (Rolle ${roleManager.currentRole})"
            return false
        }
        val selected = snapshot.assets.firstOrNull { it.id == selectedAssetId.value }
        val targets = when {
            selected != null -> listOf(selected)
            snapshot.assets.size == 1 -> snapshot.assets
            else -> emptyList()
        }
        if (targets.isEmpty()) {
            _lastError.value = "Kein Asset ausgewählt"
            return false
        }
        if (definition.wireCommand == null) {
            _lastError.value = "Kein Gerätekommando hinterlegt"
            return false
        }
        var delivered = false
        targets.forEach { asset ->
            if (agentService.sendAction(asset, definition.wireCommand.wireCommand)) delivered = true
        }
        if (!delivered) {
            _lastError.value = "Zustellung fehlgeschlagen – Aktion in Offline-Queue"
        }
        return delivered
    }

    /** Service-Aktionen nutzen die echten Services (kein Fake-Success). */
    private suspend fun executeService(key: String): Boolean = when (key) {
        "AGENT_START" -> runCatching { agentService.start(); true }.getOrDefault(false)
        "AGENT_STOP" -> {
            agentService.stop()
            true
        }
        "AGENT_CYCLE" -> runCatching { agentService.runCycle().assetsChecked >= 0 }.getOrDefault(false)
        "QUEUE_FLUSH" -> {
            val delivered = agentService.flushOfflineQueue()
            emitQueueEvent("flush", "queued→delivered: $delivered")
            delivered >= 0
        }
        "MQTT_RECONNECT" -> runCatching { mqttService.reconnect(); true }.getOrDefault(false)
        "GPS_QUERY" -> runCatching {
            val loc = satelliteService.currentLocation()
            _lastError.value = if (loc != null) {
                "GPS-Ortung OK (${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)})"
            } else {
                "GPS-Ortung nicht verfügbar (Berechtigung/Hardware)"
            }
            true
        }.getOrDefault(false)
        "BLE_SCAN", "OPTICAL_SEARCH", "DIAGNOSTICS", "PERMISSIONS_AUDIT",
        "DEVICE_INFO", "LOCK_NOW" -> {
            // Wird über den bestehenden Screen/Service-Stack ausgeführt; hier
            // wird keine erfundene Antwort erzeugt.
            _lastError.value = "Aktion '$key' im zugehörigen Screen verfügbar"
            false
        }
        else -> {
            _lastError.value = "Unbekannte Service-Aktion: $key"
            false
        }
    }

    private val selectedAssetId = MutableStateFlow<String?>(null)
    val selectedAsset: StateFlow<String?> = selectedAssetId.asStateFlow()

    fun selectAsset(id: String?) {
        selectedAssetId.value = id
    }

    fun clearError() {
        _lastError.value = null
    }

    fun consumeWebEvent() {
        _webEvents.value = null
    }

    private fun emitQueueEvent(type: String, detail: String) {
        _webEvents.value = gson.toJson(
            mapOf("type" to type, "data" to mapOf("message" to detail))
        )
    }

    /** Offline-Queue: Retry (nutzt die echte MQTT-Zustellung). */
    fun retryQueue() {
        viewModelScope.launch {
            val delivered = agentService.flushOfflineQueue()
            _lastError.value = if (delivered >= 0) null else "Queue-Retry fehlgeschlagen"
            emitQueueEvent("retry", "delivered=$delivered")
        }
    }

    /** Offline-Queue: Eintrag entfernen. */
    fun removeQueueEntry(id: Long) {
        viewModelScope.launch {
            repository.removePendingAction(id)
            emitQueueEvent("remove", "id=$id")
        }
    }

    fun acknowledgeAlert(id: Long) {
        viewModelScope.launch { repository.acknowledgeAlert(id) }
    }

    fun resolveAlert(id: Long) {
        viewModelScope.launch { repository.resolveAlert(id) }
    }

    // -------------------------------------------------- Bridge (JS → Native)

    private val _bridgeEvents = MutableStateFlow<List<String>>(emptyList())
    val bridgeEvents: StateFlow<List<String>> = _bridgeEvents.asStateFlow()

    /** Vom JS gemeldetes Ereignis über SecureGuardNative.event(json). */
    fun onJsEvent(json: String) {
        _bridgeEvents.value = (_bridgeEvents.value + json).takeLast(40)
    }

    /** JS fordert einen frischen Snapshot an; Antwort geht über die JS-Hook. */
    val pendingBridgeActions = MutableStateFlow<List<Pair<String, String>>>(emptyList())

    /** Verarbeitet SecureGuardNative.action(json) aus der WebView. */
    fun handleBridgeAction(json: String) {
        val g = gson
        val obj = runCatching { g.fromJson(json, com.google.gson.JsonObject::class.java) }.getOrNull()
        val action = obj?.get("action")?.asString ?: return
        val assetIds = obj.getAsJsonArray("assetIds")?.mapNotNull { it.asString }.orEmpty()
        assetIds.take(1).forEach { selectAsset(it) }
        execute(action)
    }

    fun onJsReady() {
        val json = snapshotJson()
        pendingBridgeActions.value = pendingBridgeActions.value +
            ("\u2028" to json) // sentinel: snapshot push
    }

    /**
     * Konsumiert ausstehende JS-Aufrufe (Snapshot-Push). Der Screen ruft die
     * Scripte via evaluateJavascript auf. Rückgabe als Liste von Script-Dupeln
     * (Kind, Payload).
     */
    fun takePendingBridgeActions(): List<Pair<String, String>> {
        val v = pendingBridgeActions.value
        pendingBridgeActions.value = emptyList()
        return v
    }

    // ------------------------------------------------------------------ JSON

    private val typeLabel = mapOf(
        DetectionSource.BLE to "BLE", DetectionSource.WIFI to "WIFI",
        DetectionSource.LORA to "LORA", DetectionSource.TELEMETRY to "TELEMETRY",
        DetectionSource.NFC to "NFC",
        DetectionSource.OPTICAL to "OPTICAL", DetectionSource.URBAN to "URBAN",
        DetectionSource.CROWD to "CROWD", DetectionSource.SATELLITE to "SATELLITE",
        DetectionSource.MQTT to "MQTT", DetectionSource.WEBSOCKET to "WEBSOCKET"
    )

    private fun toJsMap(s: OpsSnapshot): Map<String, Any> {
        val channelLoad = LinkedHashMap<String, Int>()
        typeLabel.values.forEach { channelLoad[it] = 0 }
        val max = s.detections.groupingBy { typeLabel[it.sourceType] ?: "UNKNOWN" }.eachCount().values.maxOrNull() ?: 1
        s.detections.groupingBy { typeLabel[it.sourceType] ?: "UNKNOWN" }.eachCount().forEach { (k, v) ->
            if (channelLoad.containsKey(k)) channelLoad[k] = (v * 100) / max
        }
        return mapOf(
            "source" to "NATIVE",
            "takenAt" to s.takenAt,
            "agent" to mapOf(
                "running" to s.agent.running,
                "online" to s.agent.running,
                "cycle" to s.agent.cycle,
                "uptimeMs" to s.agent.uptimeMillis,
                "detectionsThisCycle" to s.agent.detectionsThisCycle
            ),
            "assets" to s.assets.map { a ->
                mapOf(
                    "id" to a.id,
                    "mac" to a.mac,
                    "name" to a.name,
                    "shortName" to a.shortName,
                    "status" to a.status.name,
                    "rssi" to a.rssi,
                    "batteryLevel" to (a.batteryLevel ?: 0),
                    "latitude" to a.latitude,
                    "longitude" to a.longitude,
                    "lastSeen" to (a.lastSeen?.time ?: 0L)
                )
            },
            "alarms" to s.alarms.map { al ->
                mapOf(
                    "id" to al.id,
                    "assetId" to al.assetId,
                    "severity" to al.severity.name,
                    "type" to al.type.name,
                    "message" to al.message,
                    "acknowledged" to al.acknowledged,
                    "resolved" to al.resolved,
                    "timestamp" to al.timestamp.time
                )
            },
            "detections" to s.detections.map { d ->
                mapOf(
                    "id" to d.id,
                    "assetMac" to d.assetMac,
                    "sourceType" to (typeLabel[d.sourceType] ?: d.sourceType.name),
                    "rssi" to d.rssi,
                    "latitude" to d.latitude,
                    "longitude" to d.longitude,
                    "timestamp" to d.timestamp.time
                )
            },
            "queue" to s.pendingActions.map { q ->
                mapOf(
                    "id" to q.id,
                    "action" to q.actionType,
                    "assetId" to q.assetMac,
                    "status" to if (q.attempts > 0) "retry" else "queued",
                    "attempts" to q.attempts,
                    "lastError" to (q.lastError ?: "")
                )
            },
            "channelLoad" to channelLoad,
            "nodes" to s.nodeCount,
            "connectivity" to mapOf(
                "mqtt" to s.mqttConnected,
                "websocket" to s.websocketConfigured
            )
        )
    }
}

data class OpsHistoryEntry(
    val key: String,
    val label: String,
    val risk: com.secureguard.enterprise.presentation.ui.common.ActionRisk,
    val at: Long,
    val success: Boolean
)
