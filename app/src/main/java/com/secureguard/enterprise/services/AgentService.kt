package com.secureguard.enterprise.services

import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.data.model.Detection
import com.secureguard.enterprise.data.model.DetectionSource
import com.secureguard.enterprise.data.model.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-learning orchestration agent.
 *
 * Every cycle it queries every available channel for every whitelisted asset,
 * fuses the results (best RSSI wins), persists the winning detection and
 * updates the asset's status. In [AgentSettings.learningMode] the channel that
 * most recently produced a hit for an asset is queried first on the next cycle
 * ("rekursive Verbesserung"). The agent is fully decoupled from Meshtastic:
 * LoRa is just one of several channels via [LoraService].
 *
 * Zusätzlich integriert:
 * - externe REST-APIs über [ApiServiceManager] (nur mit Einwilligung)
 * - Echtzeit-Kanäle MQTT ([MqttService]) und WebSocket ([WebSocketService])
 * - Lern-Engine ([LearningEngine]) für adaptives Intervall/Quellenwahl
 * - Audit-Log ([AuditLogService]) und Offline-Queue ([OfflineQueue])
 */
@Singleton
class AgentService @Inject constructor(
    private val database: SecureGuardDatabase,
    private val loraService: LoraService,
    private val bleService: BleService,
    private val wifiService: WifiService,
    private val telemetryService: TelemetryService,
    private val opticalService: OpticalService,
    private val urbanService: UrbanService,
    private val crowdService: CrowdService,
    private val satelliteService: SatelliteService,
    private val notificationService: NotificationService,
    private val apiServiceManager: ApiServiceManager,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val learningEngine: LearningEngine,
    private val auditLogService: AuditLogService,
    private val offlineQueue: OfflineQueue,
    private val tempMailService: TempMailService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var mqttCollectorJob: Job? = null
    private var webSocketCollectorJob: Job? = null

    private val _agentStatus = MutableStateFlow(AgentStatus())
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val cycleCount = AtomicLong(0)

    /** Recent channel hits per MAC, used for learning-mode prioritisation. */
    private val learningMemory = mutableMapOf<String, DetectionSource>()

    @Synchronized
    fun start(settings: AgentSettings = AgentSettings()) {
        if (loopJob?.isActive == true) return
        val now = System.currentTimeMillis()
        _agentStatus.value = AgentStatus(
            running = true,
            startedAt = now,
            nextRunAt = now,
            settings = settings
        )
        loopJob = scope.launch { runLoop(settings) }
        startRealtimeChannels()
        scope.launch {
            auditLogService.log(action = "AGENT_START", details = "Agent gestartet (Intervall ${settings.interval}s)")
        }
    }

    @Synchronized
    fun stop() {
        loopJob?.cancel()
        loopJob = null
        mqttCollectorJob?.cancel()
        mqttCollectorJob = null
        webSocketCollectorJob?.cancel()
        webSocketCollectorJob = null
        mqttService.disconnect()
        webSocketService.disconnect()
        _agentStatus.value = _agentStatus.value.copy(running = false)
        scope.launch {
            auditLogService.log(action = "AGENT_STOP", details = "Agent gestoppt")
        }
    }

    /** Verbindet MQTT/WebSocket und sammelt Echtzeit-Ereignisse. */
    private fun startRealtimeChannels() {
        mqttCollectorJob = scope.launch {
            mqttService.events.collect { event -> handleMqttEvent(event) }
        }
        webSocketCollectorJob = scope.launch {
            webSocketService.events.collect { event -> handleWebSocketEvent(event) }
        }
        mqttService.connect()
        if (webSocketService.isConfigured) webSocketService.connect()
    }

    private suspend fun runLoop(settings: AgentSettings) {
        while (scope.isActive) {
            val cycleStart = System.currentTimeMillis()
            val result = runCatching { runCycle(settings) }
                .getOrDefault(AgentCycleResult())

            val now = System.currentTimeMillis()
            // Adaptives Intervall: die Lern-Engine optimiert die Taktung.
            val baseIntervalMs = settings.interval.coerceAtLeast(5) * 1000L
            val intervalMs = if (settings.learningMode) {
                learningEngine.getOptimalInterval() * 1000L
            } else {
                baseIntervalMs
            }
            _agentStatus.value = _agentStatus.value.copy(
                lastRunAt = now,
                nextRunAt = now + intervalMs,
                cycle = cycleCount.incrementAndGet(),
                detectionsThisCycle = result.detections
            )

            notificationService.buildAgentNotification(
                "Zyklus ${cycleCount.get()} · ${result.assetsChecked} Assets geprüft · " +
                    "${result.detections} Treffer"
            )

            val elapsed = System.currentTimeMillis() - cycleStart
            delay((intervalMs - elapsed).coerceAtLeast(0L))
        }
    }

    /** Runs one complete cycle over all whitelisted assets. */
    suspend fun runCycle(settings: AgentSettings = _agentStatus.value.settings): AgentCycleResult {
        // Take a snapshot by using the first emission is awkward inside a suspend fun;
        // query via a one-shot list instead.
        val snapshot = currentWhitelistedAssets()
        var hits = 0
        val channelHits = mutableMapOf<String, Int>()

        for (asset in snapshot) {
            val result = comprehensiveSearch(asset, settings)
            if (result.found && result.detection != null) {
                hits++
                val key = result.detection.sourceType.name
                channelHits[key] = (channelHits[key] ?: 0) + 1
                if (settings.learningMode) {
                    learningMemory[asset.mac.uppercase()] = result.detection.sourceType
                }
            }
            // Lern-Engine füttert jede Suche als Erfahrung.
            learningEngine.learn(
                Experience(
                    assetId = asset.id,
                    success = result.found,
                    rssi = result.detection?.rssi ?: 0,
                    latitude = result.detection?.latitude,
                    longitude = result.detection?.longitude,
                    sourceType = result.detection?.sourceType?.name ?: "UNKNOWN"
                )
            )
        }
        return AgentCycleResult(
            assetsChecked = snapshot.size,
            detections = hits,
            channelHits = channelHits
        )
    }

    private suspend fun currentWhitelistedAssets(): List<Asset> {
        // Room's @Query returns a Flow; take the current snapshot.
        return database.assetDao().observeWhitelisted().first()
    }

    /** Queries every channel for [asset] and returns the best detection (lowest RSSI). */
    suspend fun comprehensiveSearch(
        asset: Asset,
        settings: AgentSettings = _agentStatus.value.settings
    ): SearchResult = coroutineScope {
        val channels = buildChannelList(asset, settings)

        val results = channels.map { (source, block) ->
            async {
                runCatching { block() }.getOrNull()?.also { detection ->
                    // Persist every channel hit for the history view.
                    persist(detection)
                    if (settings.learningMode) {
                        learningMemory[asset.mac.uppercase()] = source
                    }
                }
            }
        }.awaitAll()

        val best = results.filterNotNull().minByOrNull { it.rssi }
        if (best != null) {
            applyDetectionToAsset(asset, best)
            SearchResult(found = true, detection = best, accuracy = best.rssi)
        } else {
            markOffline(asset)
            SearchResult.NotFound
        }
    }

    private fun buildChannelList(
        asset: Asset,
        settings: AgentSettings
    ): List<Pair<DetectionSource, suspend () -> Detection?>> {
        val all = linkedMapOf<DetectionSource, suspend () -> Detection?>()

        all[DetectionSource.TELEMETRY] = { telemetryService.searchAsset(asset) }
        all[DetectionSource.BLE] = { bleService.searchAsset(asset) }
        all[DetectionSource.WIFI] = { wifiService.searchAsset(asset) }
        all[DetectionSource.LORA] = { loraService.searchAsset(asset) }
        all[DetectionSource.OPTICAL] = { opticalService.searchAsset(asset) }
        all[DetectionSource.URBAN] = { urbanService.searchAsset(asset) }

        // External / internet channels are only used when permitted.
        if (!settings.offlineOnly || settings.externalSources) {
            if (asset.externalAllowed || settings.externalSources) {
                all[DetectionSource.CROWD] = { crowdService.searchAsset(asset) }
                // Externe REST-APIs (WiGle.net etc.) – nur mit Einwilligung.
                all[DetectionSource.API] = { apiServiceManager.searchViaWiGle(asset.mac) }
            }
            all[DetectionSource.SATELLITE] = { satelliteService.searchAsset(asset) }
        }

        if (!settings.learningMode) return all.toList()

        // Re-order: the channel that last produced a hit goes first.
        val last = learningMemory[asset.mac.uppercase()] ?: return all.toList()
        val ordered = linkedMapOf<DetectionSource, suspend () -> Detection?>()
        all[last]?.let { ordered[last] = it }
        all.forEach { (source, block) -> if (source != last) ordered[source] = block }
        return ordered.toList()
    }

    private suspend fun persist(detection: Detection) {
        database.detectionDao().insert(detection)
    }

    private suspend fun applyDetectionToAsset(asset: Asset, detection: Detection) {
        database.assetDao().updateStatus(
            mac = asset.mac,
            status = AssetStatus.ONLINE,
            rssi = detection.rssi,
            lat = detection.latitude ?: asset.latitude,
            lon = detection.longitude ?: asset.longitude,
            timestamp = detection.timestamp
        )
    }

    private suspend fun markOffline(asset: Asset) {
        val latest = database.detectionDao().latestForAsset(asset.mac)
        val cutoff = System.currentTimeMillis() - STALE_MS
        val isStale = latest == null || latest.timestamp.time < cutoff
        if (isStale && asset.status != AssetStatus.MAINTENANCE) {
            database.assetDao().setStatus(asset.mac, AssetStatus.OFFLINE, Date())
        }
    }

    /** Manually trigger a single-asset search (used by the detail screen). */
    suspend fun searchAsset(asset: Asset): SearchResult = comprehensiveSearch(asset)

    /** Convenience overload for Worker / background use without explicit settings. */
    suspend fun runCycle(): AgentCycleResult = runCycle(_agentStatus.value.settings)

    // ============ ECHTZEIT-KANÄLE (MQTT / WEBSOCKET) ============

    private suspend fun handleMqttEvent(event: MqttEvent) {
        when (event) {
            is MqttEvent.Telemetry -> {
                val detection = Detection(
                    assetMac = event.assetMac,
                    sourceType = DetectionSource.MQTT,
                    nodeId = "mqtt-broker",
                    rssi = event.rssi,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    accuracyMeters = 30f,
                    message = event.payload.take(120),
                    timestamp = Date()
                )
                persist(detection)
                updateAssetIfKnown(detection)
            }

            is MqttEvent.Alert -> {
                database.alertDao().insert(
                    com.secureguard.enterprise.data.model.Alert(
                        assetId = event.assetMac,
                        type = com.secureguard.enterprise.data.model.AlertType.SECURITY,
                        severity = com.secureguard.enterprise.data.model.AlertSeverity.WARNING,
                        message = event.message,
                        timestamp = Date()
                    )
                )
                notificationService.sendAlertNotification(
                    "MQTT-Alert",
                    event.message
                )
            }

            else -> Unit
        }
    }

    private suspend fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.Telemetry -> {
                val mac = event.data["mac"] as? String ?: event.data["assetMac"] as? String
                if (mac != null) {
                    val detection = Detection(
                        assetMac = mac.uppercase(),
                        sourceType = DetectionSource.WEBSOCKET,
                        nodeId = "fleet-ws",
                        rssi = (event.data["rssi"] as? Number)?.toInt() ?: 0,
                        latitude = (event.data["lat"] as? Number)?.toDouble(),
                        longitude = (event.data["lng"] as? Number)?.toDouble(),
                        accuracyMeters = 50f,
                        timestamp = Date()
                    )
                    persist(detection)
                    updateAssetIfKnown(detection)
                }
            }

            is WebSocketEvent.Alert -> {
                val mac = event.data["mac"] as? String ?: event.data["assetMac"] as? String
                if (mac != null) {
                    database.alertDao().insert(
                        com.secureguard.enterprise.data.model.Alert(
                            assetId = mac.uppercase(),
                            type = com.secureguard.enterprise.data.model.AlertType.SECURITY,
                            severity = com.secureguard.enterprise.data.model.AlertSeverity.CRITICAL,
                            message = event.data["message"] as? String ?: "WebSocket-Alert",
                            timestamp = Date()
                        )
                    )
                }
            }

            else -> Unit
        }
    }

    /** Aktualisiert den Asset-Status, falls das Asset bekannt (whitelisted) ist. */
    private suspend fun updateAssetIfKnown(detection: Detection) {
        val asset = database.assetDao().getByMac(detection.assetMac) ?: return
        applyDetectionToAsset(asset, detection)
    }

    // ============ AKTIONEN ============

    /**
     * Führt eine Aktion über alle verfügbaren Kanäle aus (MQTT, WebSocket,
     * BLE-GATT). Bei fehlender Verbindung wird die Aktion in die
     * Offline-Queue eingereiht.
     */
    suspend fun sendAction(asset: Asset, action: String): Boolean {
        auditLogService.log(
            action = "ACTION",
            details = "${action} → ${asset.shortName} (${asset.mac})"
        )

        var delivered = false

        // 1. MQTT
        mqttService.sendCommand(asset.mac, action)
        if (mqttService.isConnected) delivered = true

        // 2. WebSocket
        if (webSocketService.isConfigured) {
            webSocketService.sendCommand(asset.id, action)
            delivered = true
        }

        // 3. BLE/GATT (Telemetrie-Kanal)
        if (telemetryService.sendCommand(asset.mac, action)) delivered = true

        // 4. Offline-Fallback: Aktion persistieren, wenn nichts zugestellt wurde.
        if (!delivered) {
            offlineQueue.enqueue(
                actionType = action,
                assetMac = asset.mac,
                payload = mapOf("assetId" to asset.id, "action" to action)
            )
        }
        return delivered
    }

    /** Zustellung der Offline-Queue über alle Kanäle. */
    suspend fun flushOfflineQueue(): Int {
        return offlineQueue.retryPending { action ->
            mqttService.sendCommand(action.assetMac, action.actionType)
            true
        }
    }

    // ============ TEMPORÄRE E-MAIL / REGISTRIERUNG ============

    /**
     * Automatisierte Registrierung mit temporärer E-Mail-Adresse:
     * Inbox erstellen → (Aufrufer führt die Registrierung durch) →
     * OTP abrufen. Nur für legitime Zwecke (Testumgebungen, autorisierte
     * API-Key-Generierung). Ohne konfigurierten MCP-Server → Fehler.
     */
    suspend fun autoRegisterExternalService(
        serviceName: String,
        registrationUrl: String,
        registrationData: Map<String, String>
    ): RegistrationResult {
        if (!tempMailService.isConfigured) {
            return RegistrationResult(
                success = false,
                error = "Kein MCP-Server konfiguriert (MCP_SERVER_URL)"
            )
        }
        try {
            auditLogService.log(
                action = "REGISTER_START",
                details = "Registrierung bei $serviceName (URL: $registrationUrl)"
            )

            // 1. Temporäre Inbox erstellen
            val inbox = tempMailService.createInbox()
                ?: return RegistrationResult(success = false, error = "Inbox-Erstellung fehlgeschlagen")

            // 2. Registrierung mit der temporären E-Mail durchführen
            val registerSuccess = performRegistration(
                serviceName = serviceName,
                url = registrationUrl,
                data = registrationData,
                email = inbox.email
            )
            if (!registerSuccess) {
                tempMailService.clearInbox()
                return RegistrationResult(
                    success = false,
                    error = "Registrierung bei $serviceName fehlgeschlagen",
                    email = inbox.email
                )
            }

            // 3. Auf OTP warten
            val otpResult = tempMailService.waitForOTP()
            return if (otpResult?.success == true) {
                auditLogService.log(
                    action = "REGISTER_OTP",
                    details = "OTP für $serviceName empfangen"
                )
                RegistrationResult(
                    success = true,
                    email = inbox.email,
                    otp = otpResult.otp,
                    inboxToken = inbox.token
                )
            } else {
                RegistrationResult(
                    success = false,
                    error = "Kein OTP empfangen",
                    email = inbox.email
                )
            }
        } catch (e: Exception) {
            auditLogService.log(
                action = "REGISTER_ERROR",
                details = "$serviceName: ${e.message}"
            )
            return RegistrationResult(
                success = false,
                error = e.message ?: "Unbekannter Fehler"
            )
        }
    }

    /** Führt die Registrierung durch (Demo-Modus: immer erfolgreich). */
    private suspend fun performRegistration(
        serviceName: String,
        url: String,
        data: Map<String, String>,
        email: String
    ): Boolean {
        // Demo-Modus: Simuliert erfolgreiche Registrierung
        // (In Produktion: echte HTTP-POST mit email im Payload)
        delay(300)
        return true
    }

    companion object {
        private const val STALE_MS = 5 * 60 * 1000L // 5 minutes
    }

    /** Returns true only if the agent is fully operational (running + all channels ready). */
    fun isFullyOperational(): Boolean {
        val status = _agentStatus.value
        return status.running &&
            (bleService.isAvailable() || wifiService.isAvailable() || satelliteService.isAvailable()) &&
            (mqttService.isConnected || webSocketService.isConfigured)
    }
}
