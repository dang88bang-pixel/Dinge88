package com.secureguard.enterprise.agent

import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.services.ApiServiceManager
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.TempMailService
import com.secureguard.enterprise.services.WebSocketService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ApiNodeManager – autonomer Agent zur Verwaltung aller externen
 * API-Abfrageknotenpunkte.
 *
 * Der Manager entscheidet selbstständig, welche Quellen wann abgefragt
 * werden: Priorisierung nach Erfolgsquote (Learning Layer), Health-Monitor
 * mit Circuit-Breaker (3 Fehlversuche → Knoten offline), Ratenlimits und
 * Timeouts pro Knoten. Alle Treffer werden als [Detection]-Flow emittiert
 * und im Audit-Log protokolliert.
 */
@Singleton
class ApiNodeManager @Inject constructor(
    private val apiServiceManager: ApiServiceManager,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val tempMailService: TempMailService,
    private val auditLogService: AuditLogService
) {

    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
        private const val CACHE_TTL = 300_000L // 5 Minuten
        private const val CIRCUIT_BREAKER_THRESHOLD = 3
        private const val CIRCUIT_BREAKER_COOLDOWN_MS = 300_000L // 5 Minuten
    }

    // ============ STATUS ============
    private val _nodeStatus = MutableStateFlow<Map<String, NodeStatus>>(emptyMap())
    val nodeStatus: StateFlow<Map<String, NodeStatus>> = _nodeStatus.asStateFlow()

    private val _detections = MutableSharedFlow<Detection>(extraBufferCapacity = 100)
    val detections: SharedFlow<Detection> = _detections.asSharedFlow()

    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying.asStateFlow()

    // ============ KNOTEN-REGISTRY ============
    private val nodeRegistry = ConcurrentHashMap<String, NodeDefinition>()

    // Vom Nutzer deaktivierte Knoten (Toggle in der UI).
    private val disabledNodes = ConcurrentHashMap.newKeySet<String>()

    // Lern-Daten: Erfolgshistorie + Fehlversuche pro Knoten.
    private val successHistory = ConcurrentHashMap<String, ArrayDeque<Boolean>>()
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()
    private val lastFailureAt = ConcurrentHashMap<String, Long>()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var healthMonitorJob: Job? = null
    private var learningLoopJob: Job? = null

    init {
        registerAllNodes()
        startLoops()
    }

    /**
     * Startet Health-Monitor + Learning-Loop (idempotent). Nach [shutdown]
     * werden die Loops beim nächsten [queryAllNodes] automatisch wieder
     * gestartet – der Manager bleibt damit steuerbar (F-38) und läuft nicht
     * unnötig im Hintergrund, wenn der Agent gestoppt wurde.
     */
    @Synchronized
    fun startLoops() {
        if (healthMonitorJob?.isActive != true) healthMonitorJob = startHealthMonitor()
        if (learningLoopJob?.isActive != true) learningLoopJob = startLearningLoop()
    }

    /** Stoppt Hintergrund-Loops (z. B. wenn der Agent gestoppt wird). */
    @Synchronized
    fun shutdown() {
        healthMonitorJob?.cancel()
        healthMonitorJob = null
        learningLoopJob?.cancel()
        learningLoopJob = null
    }

    // ============ NODES REGISTRIEREN ============

    private fun registerAllNodes() {
        registerNode(
            id = "wigle",
            name = "WiGle.net",
            type = NodeType.API,
            handler = { ctx -> searchViaWiGle(ctx) },
            priority = DefaultNodeConfigs.WIGLE.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.WIGLE.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.WIGLE.timeoutMs
        )
        registerNode(
            id = "maclookup",
            name = "MacLookup.app",
            type = NodeType.API,
            handler = { ctx -> searchViaMacLookup(ctx) },
            priority = DefaultNodeConfigs.MACLOOKUP.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.MACLOOKUP.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.MACLOOKUP.timeoutMs
        )
        registerNode(
            id = "openchargemap",
            name = "Open Charge Map",
            type = NodeType.API,
            handler = { ctx -> searchViaOpenChargeMap(ctx) },
            priority = DefaultNodeConfigs.OPEN_CHARGE_MAP.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.OPEN_CHARGE_MAP.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.OPEN_CHARGE_MAP.timeoutMs
        )
        registerNode(
            id = "dhl",
            name = "DHL Packstationen",
            type = NodeType.API,
            handler = { ctx -> searchViaDHL(ctx) },
            priority = DefaultNodeConfigs.DHL.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.DHL.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.DHL.timeoutMs
        )
        registerNode(
            id = "ckan",
            name = "CKAN Open Data",
            type = NodeType.API,
            handler = { ctx -> searchViaCKAN(ctx) },
            priority = DefaultNodeConfigs.CKAN.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.CKAN.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.CKAN.timeoutMs
        )
        registerNode(
            id = "googlegeo",
            name = "Google Geolocation",
            type = NodeType.API,
            handler = { ctx -> searchViaGoogleGeo(ctx) },
            priority = DefaultNodeConfigs.GOOGLE_GEO.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.GOOGLE_GEO.rateLimitPerMinute),
            timeoutMs = DefaultNodeConfigs.GOOGLE_GEO.timeoutMs,
            requiresAuth = DefaultNodeConfigs.GOOGLE_GEO.requiresAuth
        )
        registerNode(
            id = "netatmo",
            name = "Netatmo Weather",
            type = NodeType.API,
            handler = { ctx -> searchViaNetatmo(ctx) },
            priority = DefaultNodeConfigs.NETATMO.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.NETATMO.rateLimitPerMinute),
            timeoutMs = 8_000,
            requiresAuth = true
        )
        registerNode(
            id = "helium",
            name = "Helium Network",
            type = NodeType.API,
            handler = { ctx -> searchViaHelium(ctx) },
            priority = DefaultNodeConfigs.HELIUM.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.HELIUM.rateLimitPerMinute),
            timeoutMs = 10_000
        )
        registerNode(
            id = "mqtt",
            name = "MQTT Broker",
            type = NodeType.MQTT,
            handler = { ctx -> searchViaMQTT(ctx) },
            priority = DefaultNodeConfigs.MQTT.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.MQTT.rateLimitPerMinute),
            timeoutMs = 3_000
        )
        registerNode(
            id = "websocket",
            name = "WebSocket",
            type = NodeType.WEBSOCKET,
            handler = { ctx -> searchViaWebSocket(ctx) },
            priority = DefaultNodeConfigs.WEBSOCKET.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.WEBSOCKET.rateLimitPerMinute),
            timeoutMs = 5_000
        )
        registerNode(
            id = "tempmail",
            name = "TempMail",
            type = NodeType.API,
            handler = { ctx -> searchViaTempMail(ctx) },
            priority = DefaultNodeConfigs.TEMPMAIL.priority,
            rateLimit = RateLimit(requestsPerMinute = DefaultNodeConfigs.TEMPMAIL.rateLimitPerMinute),
            timeoutMs = 45_000
        )
    }

    private fun registerNode(
        id: String,
        name: String,
        type: NodeType,
        handler: suspend (SearchContext) -> List<Detection>,
        priority: Int,
        rateLimit: RateLimit,
        timeoutMs: Long = DEFAULT_TIMEOUT,
        requiresAuth: Boolean = false
    ) {
        nodeRegistry[id] = NodeDefinition(
            id = id,
            name = name,
            type = type,
            handler = handler,
            priority = priority,
            rateLimit = rateLimit,
            timeoutMs = timeoutMs,
            requiresAuth = requiresAuth
        )
        _nodeStatus.value = _nodeStatus.value + (id to NodeStatus.UNKNOWN)
    }

    // ============ NODE-HANDLER IMPLEMENTIERUNGEN ============

    private suspend fun searchViaWiGle(context: SearchContext): List<Detection> {
        // ApiServiceManager liefert bereits eine fertige Detection (Source API).
        val detection = apiServiceManager.searchViaWiGle(context.mac) ?: return emptyList()
        return listOf(detection)
    }

    private suspend fun searchViaMacLookup(context: SearchContext): List<Detection> {
        val vendor = apiServiceManager.searchViaMacLookup(context.mac) ?: return emptyList()
        return listOf(
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = "maclookup",
                rssi = 0,
                message = "Hersteller: $vendor",
                timestamp = Date()
            )
        )
    }

    private suspend fun searchViaOpenChargeMap(context: SearchContext): List<Detection> {
        val stations = apiServiceManager.searchViaOpenChargeMap(
            context.latitude ?: 52.52,
            context.longitude ?: 13.40
        )
        return stations.map { s ->
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = s.id.toString(),
                rssi = 0,
                latitude = s.latitude,
                longitude = s.longitude,
                accuracyMeters = 50f,
                message = "Ladesäule: ${s.operator ?: "Unbekannt"} · ${s.status ?: "?"}",
                timestamp = Date()
            )
        }
    }

    private suspend fun searchViaDHL(context: SearchContext): List<Detection> {
        val stations = apiServiceManager.searchViaDHL(
            context.latitude ?: 52.52,
            context.longitude ?: 13.40
        )
        return stations.map { s ->
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = s.id ?: "dhl",
                rssi = 0,
                latitude = s.latitude,
                longitude = s.longitude,
                accuracyMeters = 80f,
                message = "Packstation: ${s.name ?: s.id ?: "?"} · frei: ${s.boxesAvailable}",
                timestamp = Date()
            )
        }
    }

    private suspend fun searchViaCKAN(context: SearchContext): List<Detection> {
        val datasets = apiServiceManager.searchViaCKAN(context.mac)
        return datasets.map { r ->
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = r.id ?: "ckan",
                rssi = 0,
                message = "Datensatz: ${r.title ?: "?"}",
                timestamp = Date()
            )
        }
    }

    private suspend fun searchViaGoogleGeo(context: SearchContext): List<Detection> {
        val accessPoints = listOf(
            com.secureguard.enterprise.services.apis.WifiAccessPoint(
                macAddress = context.mac,
                signalStrength = -45
            )
        )
        val location = apiServiceManager.searchViaGoogleGeolocation(accessPoints) ?: return emptyList()
        return listOf(
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = "google-geo",
                rssi = 0,
                latitude = location.lat,
                longitude = location.lng,
                accuracyMeters = 30f,
                message = "Google Geolocation",
                timestamp = Date()
            )
        )
    }

    private suspend fun searchViaNetatmo(context: SearchContext): List<Detection> {
        val devices = apiServiceManager.searchViaNetatmo()
        return devices.map { d ->
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = d.id ?: "netatmo",
                rssi = 0,
                latitude = d.place?.latitude,
                longitude = d.place?.longitude,
                accuracyMeters = 500f,
                message = "Wetterstation: ${d.stationName ?: d.id ?: "?"}" +
                    d.dashboardData?.temperature?.let { " · ${it}°C" }.orEmpty(),
                timestamp = Date()
            )
        }
    }

    private suspend fun searchViaHelium(context: SearchContext): List<Detection> {
        val hotspots = apiServiceManager.searchViaHelium(
            context.latitude ?: 52.52,
            context.longitude ?: 13.40
        )
        return hotspots.map { h ->
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.LORA,
                nodeId = h.address ?: "helium",
                rssi = 0,
                latitude = h.lat,
                longitude = h.lng,
                accuracyMeters = 100f,
                message = "LoRaWAN-Hotspot: ${h.name ?: h.address ?: "?"} · ${h.status ?: "?"}",
                timestamp = Date()
            )
        }
    }

    private suspend fun searchViaMQTT(context: SearchContext): List<Detection> {
        // Asynchrone Anfrage über den Broker; Antworten laufen über den
        // MqttService-Events-Flow (im AgentService verdrahtet).
        mqttService.publish("secureguard/request", context.mac)
        return emptyList()
    }

    private suspend fun searchViaWebSocket(context: SearchContext): List<Detection> {
        // Asynchrone Anfrage an die Fleet-Instanz; Antworten laufen über den
        // WebSocketService-Events-Flow (im AgentService verdrahtet).
        if (webSocketService.isConfigured) {
            webSocketService.sendMessage(
                mapOf(
                    "type" to "search",
                    "mac" to context.mac,
                    "deviceId" to context.deviceId.orEmpty()
                )
            )
        }
        return emptyList()
    }

    private suspend fun searchViaTempMail(context: SearchContext): List<Detection> {
        if (!tempMailService.isConfigured) return emptyList()
        val inbox = tempMailService.createInbox() ?: return emptyList()
        val otp = tempMailService.waitForOTP(timeoutMs = 45_000)
        return listOf(
            Detection(
                assetMac = context.mac,
                sourceType = DetectionSource.API,
                nodeId = inbox.inboxId,
                rssi = 0,
                message = if (otp?.success == true) {
                    "TempMail OTP: ${otp.otp} von ${otp.from}"
                } else {
                    "TempMail-Inbox: ${inbox.email} (kein OTP)"
                },
                timestamp = Date()
            )
        )
    }

    // ============ HEALTH-MONITOR (CIRCUIT BREAKER) ============

    private fun startHealthMonitor(): Job = monitorScope.launch {
        while (isActive) {
            nodeRegistry.keys.forEach { nodeId ->
                updateNodeStatus(nodeId, currentStatus(nodeId))
            }
            delay(60_000) // Alle 60 Sekunden
        }
    }

    /** Status eines Knotens: OFFLINE bei Circuit-Breaker, sonst ONLINE. */
    private fun currentStatus(nodeId: String): NodeStatus {
        val failures = consecutiveFailures[nodeId] ?: 0
        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            val since = lastFailureAt[nodeId] ?: 0L
            // Nach der Cooldown-Zeit wird der Knoten wieder zugelassen.
            if (System.currentTimeMillis() - since > CIRCUIT_BREAKER_COOLDOWN_MS) {
                consecutiveFailures[nodeId] = 0
                return NodeStatus.ONLINE
            }
            return NodeStatus.OFFLINE
        }
        return NodeStatus.ONLINE
    }

    private fun updateNodeStatus(nodeId: String, status: NodeStatus) {
        _nodeStatus.value = _nodeStatus.value + (nodeId to status)
    }

    // ============ LEARNING-LAYER ============

    private fun startLearningLoop(): Job = monitorScope.launch {
        while (isActive) {
            adaptPriorities()
            delay(300_000) // Alle 5 Minuten
        }
    }

    private fun adaptPriorities() {
        successHistory.forEach { (nodeId, history) ->
            if (history.size >= 10) {
                val successRate = history.count { it }.toFloat() / history.size
                val node = nodeRegistry[nodeId] ?: return@forEach
                val boost = when {
                    successRate > 0.8 -> 15
                    successRate < 0.3 -> -15
                    else -> 0
                }
                node.priorityBoost = boost
            }
        }
    }

    private fun recordSuccess(nodeId: String, success: Boolean) {
        val deque = successHistory.getOrPut(nodeId) { ArrayDeque() }
        deque.addLast(success)
        while (deque.size > 100) deque.removeFirst()

        if (success) {
            consecutiveFailures[nodeId] = 0
        } else {
            val failures = (consecutiveFailures[nodeId] ?: 0) + 1
            consecutiveFailures[nodeId] = failures
            if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
                lastFailureAt[nodeId] = System.currentTimeMillis()
            }
        }
    }

    // ============ HAUPT-ABFRAGEFUNKTION ============

    /**
     * Fragt alle aktiven Knoten ab (priorisiert, mit Ratenlimit, Timeout
     * und Circuit-Breaker) und emittiert die Treffer als [detections].
     */
    suspend fun queryAllNodes(
        mac: String,
        latitude: Double? = null,
        longitude: Double? = null,
        deviceId: String? = null
    ): List<Detection> {
        // Falls nach einem shutdown() wieder abgefragt wird: Loops starten.
        startLoops()
        val context = SearchContext(mac, latitude, longitude, deviceId)
        val results = mutableListOf<Detection>()
        _isQuerying.value = true
        try {
            val sortedNodes = nodeRegistry.values
                .filter { it.id !in disabledNodes }
                .filter { currentStatus(it.id) != NodeStatus.OFFLINE }
                .sortedByDescending { it.priority + it.priorityBoost }

            for (node in sortedNodes) {
                if (!node.rateLimit.tryAcquire()) {
                    updateNodeStatus(node.id, NodeStatus.RATE_LIMITED)
                    continue
                }
                try {
                    val detections = withTimeoutOrNull(node.timeoutMs) {
                        node.handler(context)
                    }.orEmpty()

                    if (detections.isNotEmpty()) {
                        results.addAll(detections)
                        detections.forEach { _detections.tryEmit(it) }
                        recordSuccess(node.id, true)
                        updateNodeStatus(node.id, NodeStatus.ONLINE)
                        auditLogService.log(
                            action = "NODE_SEARCH",
                            details = "node=${node.name}, count=${detections.size}, mac=$mac"
                        )
                    } else {
                        recordSuccess(node.id, false)
                    }
                } catch (e: Exception) {
                    recordSuccess(node.id, false)
                    updateNodeStatus(node.id, NodeStatus.ERROR)
                    auditLogService.log(
                        action = "NODE_ERROR",
                        details = "node=${node.name}, error=${e.message}"
                    )
                }
            }
        } finally {
            _isQuerying.value = false
        }
        return results
    }

    // ============ AUTONOME ENTSCHEIDUNGEN ============

    /**
     * Autonome Suche: entscheidet anhand der [SearchPriority], welche
     * Knoten abgefragt werden.
     */
    suspend fun autonomousSearch(
        assetMac: String,
        priority: SearchPriority = SearchPriority.NORMAL
    ): List<Detection> {
        val enabledNodes = when (priority) {
            SearchPriority.HIGH -> nodeRegistry.values.filter { it.id !in disabledNodes }
            SearchPriority.NORMAL -> nodeRegistry.values.filter {
                it.id !in disabledNodes && currentStatus(it.id) != NodeStatus.OFFLINE
            }
            SearchPriority.LOW -> nodeRegistry.values.filter {
                it.id !in disabledNodes &&
                    currentStatus(it.id) != NodeStatus.OFFLINE &&
                    !it.requiresAuth
            }
            SearchPriority.OFFLINE -> emptyList()
        }

        return enabledNodes.mapNotNull { node ->
            try {
                withTimeoutOrNull(node.timeoutMs) {
                    node.handler(SearchContext(assetMac))
                }.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }.flatten()
    }

    // ============ UI-UNTERSTÜTZUNG ============

    /** Deaktiviert/aktiviert einen Knoten (UI-Toggle). */
    fun toggleNode(nodeId: String) {
        if (!disabledNodes.add(nodeId)) {
            disabledNodes.remove(nodeId)
        }
        _nodeStatus.value = _nodeStatus.value + (nodeId to currentStatus(nodeId))
    }

    fun isNodeEnabled(nodeId: String): Boolean = nodeId !in disabledNodes

    /** Erzwingt eine sofortige Status-Aktualisierung aller Knoten. */
    fun refreshHealth() {
        nodeRegistry.keys.forEach { nodeId ->
            updateNodeStatus(nodeId, currentStatus(nodeId))
        }
    }
}

// ============ DATENKLASSEN ============

/** Definition eines Abfrageknotens inkl. Handler und Limits. */
data class NodeDefinition(
    val id: String,
    val name: String,
    val type: NodeType,
    val handler: suspend (SearchContext) -> List<Detection>,
    val priority: Int,
    val rateLimit: RateLimit,
    val timeoutMs: Long,
    val requiresAuth: Boolean = false,
    @Volatile var priorityBoost: Int = 0
)

/** Kontext einer Knotenabfrage. */
data class SearchContext(
    val mac: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val deviceId: String? = null,
    val maxResults: Int = 10,
    val timeoutMs: Long = 10_000
)

/** Einfaches gleitendes Ratenlimit (Anfragen pro Minute). */
class RateLimit(
    val requestsPerMinute: Int
) {
    private val timestamps = ArrayDeque<Long>()

    @Synchronized
    fun tryAcquire(now: Long = System.currentTimeMillis()): Boolean {
        while (timestamps.isNotEmpty() && now - timestamps.first() > 60_000L) {
            timestamps.removeFirst()
        }
        if (timestamps.size >= requestsPerMinute) return false
        timestamps.addLast(now)
        return true
    }
}

enum class NodeType {
    API, MQTT, WEBSOCKET, GRPC, CUSTOM
}

enum class NodeStatus {
    ONLINE, OFFLINE, ERROR, UNKNOWN, RATE_LIMITED
}

enum class SearchPriority {
    HIGH, NORMAL, LOW, OFFLINE
}
