package com.secureguard.enterprise.services

import com.secureguard.enterprise.agent.ApiNodeManager
import com.secureguard.enterprise.data.local.SecureGuardDatabase
import com.secureguard.enterprise.data.model.Alert
import com.secureguard.enterprise.data.model.AlertSeverity
import com.secureguard.enterprise.data.model.AlertType
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-learning orchestration agent.
 *
 * Every cycle it queries every available channel for every whitelisted asset,
 * fuses the results (best RSSI wins), persists the winning detection and
 * updates the asset's status. In learning mode the channel that most recently
 * produced a hit for an asset is queried first on the next cycle.
 *
 * Integrated subsystems:
 * - External REST APIs via [ApiServiceManager] (only with consent)
 * - Real-time channels MQTT ([MqttService]) and WebSocket ([WebSocketService])
 * - NFC tag detection via [NfcService]
 * - API node orchestration via [ApiNodeManager] (circuit-breaker, rate limits)
 * - Learning engine ([LearningEngine]) for adaptive interval/source selection
 * - Audit log ([AuditLogService]) and offline queue ([OfflineQueue])
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
    private val tempMailService: TempMailService,
    private val apiNodeManager: ApiNodeManager,
    private val nfcService: NfcService,
    private val usbSerialService: UsbSerialService,
    private val alertSoundManager: AlertSoundManager,
    private val errorHandler: com.secureguard.enterprise.util.ErrorHandler,
    private val backendSyncService: BackendSyncService
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var mqttCollectorJob: Job? = null
    private var webSocketCollectorJob: Job? = null
    private var nfcCollectorJob: Job? = null

    private val _agentStatus = MutableStateFlow(AgentStatus())
    val agentStatus: StateFlow<AgentStatus> = _agentStatus.asStateFlow()

    private val cycleCount = AtomicLong(0)

    /** Schützt gegen überlappende Zyklen (FGS-Loop + WorkManager-Worker). */
    private val cycleMutex = Mutex()

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
        nfcCollectorJob?.cancel()
        nfcCollectorJob = null
        mqttService.disconnect()
        webSocketService.disconnect()
        _agentStatus.value = _agentStatus.value.copy(running = false)
        scope.launch {
            auditLogService.log(action = "AGENT_STOP", details = "Agent gestoppt")
        }
    }

    /** Connects MQTT/WebSocket/NFC and collects real-time events. */
    private fun startRealtimeChannels() {
        mqttCollectorJob = scope.launch {
            mqttService.events.collect { event -> handleMqttEvent(event) }
        }
        webSocketCollectorJob = scope.launch {
            webSocketService.events.collect { event -> handleWebSocketEvent(event) }
        }
        nfcCollectorJob = scope.launch {
            nfcService.detections.collect { detection -> handleNfcDetection(detection) }
        }
        mqttService.connect()
        if (webSocketService.isConfigured) webSocketService.connect()
    }

    private suspend fun runLoop(settings: AgentSettings) {
        while (scope.isActive) {
            val cycleStart = System.currentTimeMillis()
            val result = runCatching { runCycle(settings) }
                .onFailure { errorHandler.handleError(it, "AgentCycle") }
                .getOrDefault(AgentCycleResult())

            val now = System.currentTimeMillis()
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

            notificationService.notifyAgentStatus(
                "Zyklus ${cycleCount.get()} · ${result.assetsChecked} Assets geprüft · " +
                    "${result.detections} Treffer"
            )

            val elapsed = System.currentTimeMillis() - cycleStart
            kotlinx.coroutines.delay((intervalMs - elapsed).coerceAtLeast(0L))
        }
    }

    /** Runs one complete cycle over all whitelisted assets. */
    suspend fun runCycle(settings: AgentSettings = _agentStatus.value.settings): AgentCycleResult {
        return cycleMutex.withLock {
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

            // Check for low battery alert
            if (asset.batteryLevel != null && asset.batteryLevel <= 15) {
                database.alertDao().insert(
                    Alert(
                        assetId = asset.id,
                        type = AlertType.LOW_BATTERY,
                        severity = AlertSeverity.WARNING,
                        message = "Batterie niedrig: ${asset.batteryLevel}% (${asset.shortName})",
                        timestamp = Date()
                    )
                )
            }

            // Check for maintenance alert
            if (asset.maintenanceDue) {
                database.alertDao().insert(
                    Alert(
                        assetId = asset.id,
                        type = AlertType.MAINTENANCE,
                        severity = AlertSeverity.INFO,
                        message = "Wartung fällig: ${asset.shortName}",
                        timestamp = Date()
                    )
                )
            }
        }

        // Check USB serial for asset detections
        runCatching { readUsbSerial() }
            .onFailure { errorHandler.handleError(it, "UsbSerial") }

        // LearningEngine: Vorhersagen & Muster in den Suchzyklus einbeziehen
        if (settings.learningMode && snapshot.isNotEmpty()) {
            val prediction = learningEngine.predictNextLocation()
            if (prediction != null) {
                auditLogService.log(
                    action = "PREDICTION",
                    details = "Naechster Standort: ${"%.4f".format(prediction.first)}, ${"%.4f".format(prediction.second)}"
                )
            }
            if (cycleCount.get() % 10 == 0L) {
                val patterns = learningEngine.patterns.value
                if (patterns.isNotEmpty()) {
                    auditLogService.log(
                        action = "PATTERNS",
                        details = "${patterns.size} Muster erkannt (Konfidenz ${"%.0f".format(learningEngine.confidence.value * 100)}%)"
                    )
                }
            }
            if (learningEngine.shouldUseExternalSources() && !settings.externalSources) {
                auditLogService.log(
                    action = "SUGGEST_EXTERNAL",
                    details = "LearningEngine empfiehlt externe Quellen (niedrige Konfidenz)"
                )
            }
            snapshot.forEach { asset ->
                val probability = learningEngine.getSuccessProbability(asset.id)
                if (probability < 0.2f) {
                    auditLogService.log(
                        action = "LOW_PROBABILITY",
                        details = "${asset.shortName}: Erfolgswahrscheinlichkeit ${"%.0f".format(probability * 100)}%"
                    )
                }
            }
        }

        // Flush offline queue when MQTT is connected
        if (mqttService.isConnected) {
            val flushed = flushOfflineQueue()
            if (flushed > 0) {
                auditLogService.log(
                    action = "QUEUE_FLUSH",
                    details = "$flushed Aktionen aus Offline-Queue zugestellt"
                )
            }
        }

        // Periodischer Backend-Sync (alle 20 Zyklen, nur wenn Online-Kanäle erlaubt)
        if (!settings.offlineOnly && backendSyncService.isConfigured && cycleCount.get() % 20L == 0L) {
            runCatching { backendSyncService.syncAll() }
                .onSuccess { r ->
                    if (r.pulled + r.pushed > 0) {
                        auditLogService.log(
                            action = "BACKEND_SYNC",
                            details = "pull=${r.pulled} push=${r.pushed}"
                        )
                    }
                }
                .onFailure { errorHandler.handleError(it, "BackendSync") }
        }

        AgentCycleResult(
            assetsChecked = snapshot.size,
            detections = hits,
            channelHits = channelHits
        )
        }
    }

    private suspend fun currentWhitelistedAssets(): List<Asset> {
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
                    persist(detection)
                    if (settings.learningMode) {
                        learningMemory[asset.mac.uppercase()] = source
                    }
                }
            }
        }.awaitAll()

        val best = results.filterNotNull().minByOrNull { it.rssi }
        if (best != null) {
            // Geofence check: alert if asset found >5km from last known position
            if (asset.latitude != null && asset.longitude != null &&
                best.latitude != null && best.longitude != null
            ) {
                val distKm = haversineKm(asset.latitude, asset.longitude, best.latitude, best.longitude)
                if (distKm > GEOFENCE_RADIUS_KM) {
                    database.alertDao().insert(
                        Alert(
                            assetId = asset.id,
                            type = AlertType.GEOFENCE,
                            severity = AlertSeverity.WARNING,
                            message = "Geofence: ${asset.shortName} ${"%.1f".format(distKm)}km entfernt",
                            timestamp = Date()
                        )
                    )
                }
            }
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

        // Satellite/GPS is a local hardware channel (device GNSS receiver),
        // not an external cloud service – always available when permitted.
        all[DetectionSource.SATELLITE] = { satelliteService.searchAsset(asset) }

        // External / internet channels are only used when permitted.
        if (!settings.offlineOnly || settings.externalSources) {
            if (asset.externalAllowed || settings.externalSources) {
                all[DetectionSource.CROWD] = { crowdService.searchAsset(asset) }
            }
        }

        // API channel via ApiNodeManager (circuit-breaker, rate limits, health monitoring)
        if (!settings.offlineOnly || settings.externalSources) {
            all[DetectionSource.API] = {
                val apiDetections = apiNodeManager.queryAllNodes(
                    mac = asset.mac,
                    latitude = asset.latitude,
                    longitude = asset.longitude
                )
                apiDetections.firstOrNull()
            }
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

    // ============ REAL-TIME CHANNELS (MQTT / WEBSOCKET / NFC) ============

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
                    Alert(
                        assetId = event.assetMac,
                        type = AlertType.SECURITY,
                        severity = AlertSeverity.WARNING,
                        message = event.message,
                        timestamp = Date()
                    )
                )
                notificationService.sendAlertNotification("MQTT-Alert", event.message)
                alertSoundManager.playForSeverity("WARNING")
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
                        Alert(
                            assetId = mac.uppercase(),
                            type = AlertType.CRITICAL,
                            severity = AlertSeverity.CRITICAL,
                            message = event.data["message"] as? String ?: "Kritischer WebSocket-Alert",
                            timestamp = Date()
                        )
                    )
                    alertSoundManager.playForSeverity("CRITICAL")
                }
            }

            else -> Unit
        }
    }

    /** Handles NFC tag detections collected from NfcService. */
    private suspend fun handleNfcDetection(detection: Detection) {
        persist(detection)
        updateAssetIfKnown(detection)
        notificationService.sendAlertNotification(
            "NFC-Tag erkannt",
            "Asset ${detection.assetMac} per NFC identifiziert"
        )
        auditLogService.log(
            action = "NFC_TAG",
            details = "Tag gelesen: ${detection.assetMac}"
        )
    }

    /** Updates the asset status if the asset is known (whitelisted). */
    private suspend fun updateAssetIfKnown(detection: Detection) {
        val asset = database.assetDao().getByMac(detection.assetMac) ?: return
        applyDetectionToAsset(asset, detection)
    }

    // ============ ACTIONS ============

    /**
     * Sends an action via all available channels (MQTT, WebSocket, BLE/GATT).
     * If no channel delivers, the action is enqueued in the offline queue.
     */
    suspend fun sendAction(asset: Asset, action: String): Boolean {
        auditLogService.log(
            action = "ACTION",
            details = "${action} → ${asset.shortName} (${asset.mac})"
        )

        var delivered = false

        // 1. MQTT (Ergebnis des Publish, nicht der globale Verbindungsstatus)
        if (mqttService.sendCommand(asset.mac, action)) delivered = true

        // 2. WebSocket (nur wenn tatsächlich gesendet)
        if (webSocketService.isConfigured && webSocketService.sendCommand(asset.id, action)) {
            delivered = true
        }

        // 3. BLE/GATT (telemetry channel)
        if (telemetryService.sendCommand(asset.mac, action)) delivered = true

        // 4. Offline fallback: persist action when nothing was delivered.
        if (!delivered) {
            val payload = mapOf("assetId" to asset.id, "action" to action)
            val jsonPayload = com.google.gson.Gson().toJson(payload)
            if (offlineQueue.isValidPayload(jsonPayload)) {
                offlineQueue.enqueue(
                    actionType = action,
                    assetMac = asset.mac,
                    payload = payload
                )
            }
        }
        return delivered
    }

    /** Delivers pending actions from the offline queue via MQTT. */
    suspend fun flushOfflineQueue(): Int {
        if (!mqttService.isConnected) return 0
        return offlineQueue.retryPending { action ->
            mqttService.sendCommand(action.assetMac, action.actionType)
        }
    }

    // ============ TEMPORARY EMAIL / REGISTRATION ============

    /**
     * Automated registration with a temporary email address:
     * Create inbox → perform registration → retrieve OTP.
     * Only for legitimate purposes (test environments, authorised API key generation).
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

            val inbox = tempMailService.createInbox()
                ?: return RegistrationResult(success = false, error = "Inbox-Erstellung fehlgeschlagen")

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

    /**
     * Performs the actual HTTP POST registration with the temporary email
     * address included in the payload.
     */
    private suspend fun performRegistration(
        serviceName: String,
        url: String,
        data: Map<String, String>,
        email: String
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val payload = JSONObject(data.toMutableMap().apply { put("email", email) })
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful

            auditLogService.log(
                action = "REGISTER_HTTP",
                details = "$serviceName: HTTP ${response.code} (${if (success) "OK" else "FEHLER"})"
            )

            response.close()
            success
        } catch (e: Exception) {
            auditLogService.log(
                action = "REGISTER_HTTP_ERROR",
                details = "$serviceName: ${e.message}"
            )
            false
        }
    }

    /**
     * Reads data from USB serial (FTDI/CP210x) and creates a detection
     * if the data contains a known asset MAC.
     */
    suspend fun readUsbSerial(): Detection? {
        val line = usbSerialService.readLine() ?: return null
        // Try to extract a MAC address from the serial data
        val macPattern = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
        val mac = macPattern.find(line)?.value?.uppercase() ?: return null

        val asset = database.assetDao().getByMac(mac) ?: return null

        return Detection(
            assetMac = mac,
            sourceType = DetectionSource.UNKNOWN,
            nodeId = "usb-serial",
            rssi = 0,
            latitude = asset.latitude,
            longitude = asset.longitude,
            accuracyMeters = 1f,
            message = "USB-Serial: ${line.take(80)}",
            timestamp = Date()
        ).also { detection ->
            persist(detection)
            updateAssetIfKnown(detection)
            auditLogService.log(
                action = "USB_SERIAL",
                details = "Asset $mac via USB-Serial erkannt"
            )
        }
    }

    companion object {
        private const val STALE_MS = 5 * 60 * 1000L // 5 minutes
        private const val GEOFENCE_RADIUS_KM = 5.0

        /** Haversine formula: distance in km between two lat/lon points. */
        private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0 // Earth radius in km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        }
    }
}
