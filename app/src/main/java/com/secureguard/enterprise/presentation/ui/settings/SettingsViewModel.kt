package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.config.EndpointSnapshot
import com.secureguard.enterprise.config.IntegrationInfo
import com.secureguard.enterprise.config.IntegrationState
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import com.secureguard.enterprise.services.BackendSyncService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.SlackService
import com.secureguard.enterprise.services.WebSocketService
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.RoleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val notificationsEnabled: Boolean = true,
    val externalCrowdAllowed: Boolean = false,
    val offlineOnly: Boolean = true,
    val learningMode: Boolean = true,
    val darkMode: Boolean = false,
    val consentGiven: Boolean = true,
    val userName: String = "Admin",
    val organization: String = "SecureGuard",
    val statusMessage: String? = null,
    // Runtime endpoints (edit without rebuild)
    val mqttBrokerUrl: String = "",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val websocketUrl: String = "",
    val mcpServerUrl: String = "",
    val backendBaseUrl: String = "",
    val loraGatewayUrl: String = "",
    val yoloServerUrl: String = "",
    val openDataApiUrl: String = "",
    val findMyProxyUrl: String = "",
    // Slack (MCP) – App-seitige Anteile, Server-Teile kommen aus der Inventur
    val slackEnabled: Boolean = true,
    val slackChannel: String = "",
    // Vollständige Anbindungs-/Abhängigkeitsliste (App + Server)
    val integrations: List<IntegrationInfo> = emptyList(),
    val integrationsCheckedAt: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentService: AgentService,
    private val backupManager: com.secureguard.enterprise.services.BackupManager,
    private val exportService: com.secureguard.enterprise.services.ExportService,
    private val offlineMapService: com.secureguard.enterprise.services.OfflineMapService,
    private val settingsStore: com.secureguard.enterprise.services.AgentSettingsStore,
    private val endpointConfig: EndpointConfig,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val backendSyncService: BackendSyncService,
    private val privacyService: com.secureguard.enterprise.services.PrivacyService,
    private val roleManager: RoleManager,
    private val slackService: SlackService,
    private val slackAlertForwarder: com.secureguard.enterprise.services.SlackAlertForwarder
) : ViewModel() {

    /** Zwischenspeicher der Server-Inventur (für UI-Updates ohne neuen Abruf). */
    private var serverDependencies: List<SlackService.ServerDependency> = emptyList()

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Quelle der Laufzeit-Endpunkte (Settings-UI überschreibt local.properties). */
    private val SOURCE_ENDPOINTS = "Einstellungen / local.properties"

    private fun load(): SettingsUiState {
        val ep = endpointConfig.snapshot()
        return SettingsUiState(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            externalCrowdAllowed = prefs.getBoolean(KEY_CROWD, false),
            offlineOnly = prefs.getBoolean(KEY_OFFLINE, true),
            learningMode = prefs.getBoolean(KEY_LEARNING, true),
            darkMode = prefs.getBoolean(KEY_DARK, false),
            consentGiven = prefs.getBoolean(KEY_CONSENT, true),
            userName = prefs.getString(KEY_USERNAME, "Admin") ?: "Admin",
            organization = prefs.getString(KEY_ORG, "SecureGuard") ?: "SecureGuard",
            mqttBrokerUrl = ep.mqttBrokerUrl,
            mqttUsername = ep.mqttUsername,
            mqttPassword = ep.mqttPassword,
            websocketUrl = ep.websocketUrl,
            mcpServerUrl = ep.mcpServerUrl,
            backendBaseUrl = ep.backendBaseUrl,
            loraGatewayUrl = ep.loraGatewayUrl,
            yoloServerUrl = ep.yoloServerUrl,
            openDataApiUrl = ep.openDataApiUrl,
            findMyProxyUrl = ep.findMyProxyUrl,
            slackEnabled = ep.slackEnabled,
            slackChannel = ep.slackChannel,
            integrations = buildIntegrations(ep, emptyList(), backendReachable = null)
        )
    }

    private fun save(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { current ->
            val next = transform(current)
            prefs.edit()
                .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
                .putBoolean(KEY_CROWD, next.externalCrowdAllowed)
                .putBoolean(KEY_OFFLINE, next.offlineOnly)
                .putBoolean(KEY_LEARNING, next.learningMode)
                .putBoolean(KEY_DARK, next.darkMode)
                .putBoolean(KEY_CONSENT, next.consentGiven)
                .putString(KEY_USERNAME, next.userName)
                .putString(KEY_ORG, next.organization)
                .apply()

            if (agentService.agentStatus.value.running) {
                agentService.stop()
                val settings = AgentSettings(
                    interval = agentService.agentStatus.value.settings.interval,
                    dynamicPriority = agentService.agentStatus.value.settings.dynamicPriority,
                    learningMode = next.learningMode,
                    offlineOnly = next.offlineOnly,
                    externalSources = next.externalCrowdAllowed
                )
                // Persistieren, damit Dashboard/Worker denselben Stand laden.
                settingsStore.save(settings)
                agentService.start(settings)
            }
            next
        }
    }

    fun setNotifications(value: Boolean) = save { it.copy(notificationsEnabled = value) }
    fun setExternalCrowd(value: Boolean) = save { it.copy(externalCrowdAllowed = value) }
    fun setOfflineOnly(value: Boolean) = save { it.copy(offlineOnly = value) }
    fun setLearning(value: Boolean) = save { it.copy(learningMode = value) }
    fun setDarkMode(value: Boolean) = save { it.copy(darkMode = value) }
    fun setConsent(value: Boolean) = save { it.copy(consentGiven = value) }
    fun setUserName(value: String) = save { it.copy(userName = value) }
    fun setOrganization(value: String) = save { it.copy(organization = value) }

    // ---- Endpoint draft fields (local UI state until "Speichern") ----
    fun setMqttBrokerUrl(v: String) = _uiState.update { it.copy(mqttBrokerUrl = v) }
    fun setMqttUsername(v: String) = _uiState.update { it.copy(mqttUsername = v) }
    fun setMqttPassword(v: String) = _uiState.update { it.copy(mqttPassword = v) }
    fun setWebsocketUrl(v: String) = _uiState.update { it.copy(websocketUrl = v) }
    fun setMcpServerUrl(v: String) = _uiState.update { it.copy(mcpServerUrl = v) }
    fun setBackendBaseUrl(v: String) = _uiState.update { it.copy(backendBaseUrl = v) }
    fun setLoraGatewayUrl(v: String) = _uiState.update { it.copy(loraGatewayUrl = v) }
    fun setYoloServerUrl(v: String) = _uiState.update { it.copy(yoloServerUrl = v) }
    fun setOpenDataApiUrl(v: String) = _uiState.update { it.copy(openDataApiUrl = v) }
    fun setFindMyProxyUrl(v: String) = _uiState.update { it.copy(findMyProxyUrl = v) }

    fun setSlackChannel(v: String) = _uiState.update { it.copy(slackChannel = v) }

    /** Schalter wirkt sofort (SlackAlertForwarder prüft ihn pro Alert). */
    fun setSlackEnabled(value: Boolean) {
        endpointConfig.update(slackEnabled = value)
        _uiState.update {
            it.copy(
                slackEnabled = value,
                integrations = buildIntegrations(
                    endpointConfig.snapshot(), serverDependencies, backendReachable = null
                ),
                statusMessage = if (value) "✅ Slack-Alarme aktiviert" else "⚪ Slack-Alarme deaktiviert"
            )
        }
    }

    /**
     * Prüft alle Anbindungen: lokale Endpunkte (MQTT live, übrige Konfiguration)
     * plus die serverseitige Inventur des Backends (`/api/system/dependencies`).
     */
    fun refreshIntegrations() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val deps = runCatching { slackService.fetchDependencies() }.getOrDefault(emptyList())
            serverDependencies = deps
            val snapshot = endpointConfig.snapshot()
            _uiState.update {
                it.copy(
                    integrations = buildIntegrations(
                        snapshot,
                        deps,
                        backendReachable = if (snapshot.backendBaseUrl.isBlank()) null else deps.isNotEmpty()
                    ),
                    integrationsCheckedAt = java.text.SimpleDateFormat(
                        "HH:mm:ss", java.util.Locale.getDefault()
                    ).format(java.util.Date()),
                    statusMessage = if (deps.isEmpty()) {
                        "⚠️ Server-Inventur leer – Backend konfiguriert/erreichbar?"
                    } else {
                        "✅ ${deps.size} Server-Abhängigkeiten geprüft"
                    }
                )
            }
        }
    }

    /**
     * Baut die komplette Liste: lokale Anbindungen (aus [EndpointSnapshot] und
     * `BuildConfig`) + serverseitige Abhängigkeiten aus der Backend-Inventur.
     * Secrets werden nur als „gesetzt/nicht gesetzt" angezeigt.
     */
    private fun buildIntegrations(
        ep: EndpointSnapshot,
        serverDeps: List<SlackService.ServerDependency>,
        backendReachable: Boolean?
    ): List<IntegrationInfo> {
        fun endpoint(
            id: String,
            name: String,
            kind: String,
            url: String,
            detail: String = ""
        ) = IntegrationInfo(
            id = id,
            name = name,
            kind = kind,
            target = url.ifBlank { "nicht gesetzt" },
            state = if (url.isBlank()) IntegrationState.DISABLED else IntegrationState.CONFIGURED,
            source = SOURCE_ENDPOINTS,
            detail = detail
        )

        fun externalApi(
            id: String,
            name: String,
            key: String,
            url: String,
            needsKey: Boolean = true
        ) = IntegrationInfo(
            id = id,
            name = name,
            kind = "api",
            target = url,
            state = when {
                !needsKey -> IntegrationState.CONFIGURED
                key.isNotBlank() -> IntegrationState.CONFIGURED
                else -> IntegrationState.DISABLED
            },
            source = "local.properties (BuildConfig)",
            detail = if (needsKey) "API-Key: " + IntegrationInfo.secretState(key) else "ohne Key"
        )

        val local = mutableListOf<IntegrationInfo>()

        local += IntegrationInfo(
            id = "backend-app",
            name = "SecureGuard Backend (FastAPI)",
            kind = "http",
            target = ep.backendBaseUrl.ifBlank { "nicht gesetzt" },
            state = when {
                ep.backendBaseUrl.isBlank() -> IntegrationState.DISABLED
                backendReachable == true -> IntegrationState.CONNECTED
                backendReachable == false -> IntegrationState.MISSING
                else -> IntegrationState.CONFIGURED
            },
            source = SOURCE_ENDPOINTS,
            detail = "REST + WebSocket + Slack-/MCP-Bridge"
        )
        local += endpoint("websocket-app", "WebSocket (Echtzeit)", "ws", ep.websocketUrl)
        local += IntegrationInfo(
            id = "mqtt-app",
            name = "MQTT-Broker (Mosquitto)",
            kind = "broker",
            target = ep.mqttBrokerUrl.ifBlank { "nicht gesetzt" },
            state = when {
                mqttService.isConnected -> IntegrationState.CONNECTED
                ep.mqttBrokerUrl.isBlank() -> IntegrationState.DISABLED
                else -> IntegrationState.CONFIGURED
            },
            source = SOURCE_ENDPOINTS,
            detail = if (ep.mqttUsername.isBlank()) "anonym" else "User: ${ep.mqttUsername}"
        )
        local += endpoint("mcp-app", "MCP / Temp-Mail Server", "mcp", ep.mcpServerUrl)
        local += IntegrationInfo(
            id = "slack-app",
            name = "Slack-Alarme (App → Backend)",
            kind = "notify",
            target = ep.slackChannel.ifBlank { "Backend-Default" },
            state = if (ep.slackEnabled) IntegrationState.CONFIGURED else IntegrationState.DISABLED,
            source = "Einstellungen",
            detail = "POST /api/slack/notify · Forwarder ab WARNING"
        )
        local += endpoint("lora-gateway", "LoRa-Gateway", "http", ep.loraGatewayUrl)
        local += endpoint("yolo-server", "YOLO-Server (Objekterkennung)", "http", ep.yoloServerUrl)
        local += endpoint("ckan", "CKAN / Open Data", "http", ep.openDataApiUrl)
        local += endpoint("findmy", "Find-My-Proxy", "http", ep.findMyProxyUrl)
        local += IntegrationInfo(
            id = "dhl",
            name = "DHL Packstation (Standorte)",
            kind = "api",
            target = ep.dhlApiUrl.ifBlank { "nicht gesetzt" },
            state = if (ep.dhlApiUrl.isBlank()) IntegrationState.DISABLED
            else IntegrationState.CONFIGURED,
            source = SOURCE_ENDPOINTS,
            detail = "Token: " + IntegrationInfo.secretState(ep.dhlApiToken)
        )
        local += IntegrationInfo(
            id = "api-key",
            name = "Backend-API-Key (X-API-Key)",
            kind = "auth",
            target = IntegrationInfo.secretState(ep.backendApiKey),
            state = if (ep.backendApiKey.isBlank()) IntegrationState.DISABLED
            else IntegrationState.CONFIGURED,
            source = SOURCE_ENDPOINTS,
            detail = "Schützt schreibende Endpunkte"
        )

        // Externe APIs (Schlüssel aus local.properties → BuildConfig)
        local += externalApi("wigle", "WiGLE (BSSID → GPS)", BuildConfig.WIGLE_API_KEY, "https://api.wigle.net")
        local += externalApi(
            "openchargemap", "OpenChargeMap (Ladesäulen)",
            BuildConfig.OPEN_CHARGE_MAP_KEY, "https://api.openchargemap.io"
        )
        local += externalApi("netatmo", "Netatmo (Wetter)", BuildConfig.NETATMO_TOKEN, "https://api.netatmo.com")
        local += externalApi("google-geo", "Google Geolocation", BuildConfig.GOOGLE_API_KEY, "https://www.googleapis.com")
        local += externalApi("helium", "Helium (LoRaWAN)", BuildConfig.HELIUM_API_KEY, "https://api.helium.io")
        local += externalApi("maclookup", "MAC-Lookup (OUI)", "", "https://api.maclookup.app", needsKey = false)

        // Firmware/Gateway (läuft über MQTT, keine eigene URL)
        local += IntegrationInfo(
            id = "esp32-gateway",
            name = "ESP32-Gateway (Firmware)",
            kind = "device",
            target = "secureguard/<MAC>/command",
            state = if (ep.mqttBrokerUrl.isBlank()) IntegrationState.DISABLED
            else IntegrationState.CONFIGURED,
            source = "MQTT-Topic",
            detail = "Befehle/Telemetrie, Konfiguration per CONFIG-Payload"
        )

        val server = serverDeps.map { dep ->
            IntegrationInfo(
                id = dep.id,
                name = dep.name,
                kind = dep.kind,
                target = dep.target.ifBlank { "nicht gesetzt" },
                state = IntegrationInfo.stateFromProbe(dep.configured, dep.reachable),
                source = "Backend (docker-compose)",
                detail = dep.detail,
                origin = IntegrationInfo.ORIGIN_SERVER
            )
        }
        return local + server
    }

    /** Speichert Endpunkte und reconnectet MQTT/WebSocket. */
    fun saveEndpoints() {
        // RBAC (F-44): Konfiguration erfordert CONFIGURE_AGENT
        if (!roleManager.require(Permission.CONFIGURE_AGENT)) {
            _uiState.update { it.copy(statusMessage = "⛔ Keine Berechtigung (Rolle ${roleManager.currentRole})") }
            return
        }
        val s = _uiState.value
        endpointConfig.update(
            mqttUrl = s.mqttBrokerUrl,
            mqttUser = s.mqttUsername,
            mqttPass = s.mqttPassword,
            websocketUrl = s.websocketUrl,
            mcpUrl = s.mcpServerUrl,
            backendUrl = s.backendBaseUrl,
            loraUrl = s.loraGatewayUrl,
            yoloUrl = s.yoloServerUrl,
            ckanUrl = s.openDataApiUrl,
            findMyUrl = s.findMyProxyUrl,
            slackEnabled = s.slackEnabled,
            slackChannel = s.slackChannel
        )
        // Apply live
        mqttService.reconnect()
        webSocketService.reconnect()
        // Slack-Forwarder (neu) starten – er war ohne Backend-URL inaktiv.
        // start() ist idempotent und beachtet den Slack-Schalter pro Alert.
        runCatching { slackAlertForwarder.start() }
        // Refresh UI from resolved values
        val snap = endpointConfig.snapshot()
        _uiState.update {
            it.copy(
                mqttBrokerUrl = snap.mqttBrokerUrl,
                mqttUsername = snap.mqttUsername,
                mqttPassword = snap.mqttPassword,
                websocketUrl = snap.websocketUrl,
                mcpServerUrl = snap.mcpServerUrl,
                backendBaseUrl = snap.backendBaseUrl,
                loraGatewayUrl = snap.loraGatewayUrl,
                yoloServerUrl = snap.yoloServerUrl,
                openDataApiUrl = snap.openDataApiUrl,
                findMyProxyUrl = snap.findMyProxyUrl,
                slackEnabled = snap.slackEnabled,
                slackChannel = snap.slackChannel,
                integrations = buildIntegrations(snap, serverDependencies, backendReachable = null),
                statusMessage = "✅ Endpunkte gespeichert · MQTT/WS reconnect"
            )
        }
    }

    fun syncBackend() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { backendSyncService.syncAll() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { r ->
                            if (r.errors.isEmpty()) {
                                "✅ Sync: ${r.pulled} geholt, ${r.pushed} gesendet"
                            } else {
                                "⚠️ Sync ${r.pulled}/${r.pushed} · ${r.errors.joinToString("; ")}"
                            }
                        },
                        onFailure = { e -> "❌ Sync fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun clearStatus() { _uiState.update { it.copy(statusMessage = null) } }

    fun createBackup() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { backupManager.createBackup() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> "✅ Backup erstellt: ${file.name}" },
                        onFailure = { e -> "❌ Backup fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { exportService.exportAssetsCsv() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> "✅ CSV exportiert: ${file.name}" },
                        onFailure = { e -> "❌ Export fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { exportService.exportPdfReport() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> "✅ PDF exportiert: ${file.name}" },
                        onFailure = { e -> "❌ PDF-Export fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun exportDetectionsCsv() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { exportService.exportDetectionsCsv() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> "✅ Detektionen exportiert: ${file.name}" },
                        onFailure = { e -> "❌ Export fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun exportEncryptedCsv() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { exportService.exportAssetsCsvEncrypted() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> "✅ Verschlüsselt exportiert: ${file.name}" },
                        onFailure = { e -> "❌ Export fehlgeschlagen: ${e.message}" }
                    )
                )
            }
        }
    }

    fun getOfflineMapUrl(): String = offlineMapService.downloadRegionUrl()

    fun restoreBackup() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val backups = backupManager.listBackups()
            val latest = backups.firstOrNull()
            if (latest != null) {
                val result = runCatching { backupManager.stageRestore(latest) }
                _uiState.update {
                    it.copy(
                        statusMessage = result.fold(
                            onSuccess = { ok -> if (ok) "✅ Restore vorbereitet: ${latest.name} (App neu starten)" else "❌ Restore fehlgeschlagen" },
                            onFailure = { e -> "❌ Restore: ${e.message}" }
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "⚠️ Keine Backups vorhanden") }
            }
        }
    }

    fun listBackups(): Int = backupManager.listBackups().size

    fun startForegroundService() {
        val intent = android.content.Intent(context, AgentForegroundService::class.java)
        intent.action = AgentForegroundService.ACTION_START
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
        _uiState.update { it.copy(statusMessage = "✅ Agent als Vordergrund-Dienst gestartet") }
    }

    fun stopForegroundService() {
        val intent = android.content.Intent(context, AgentForegroundService::class.java)
        intent.action = AgentForegroundService.ACTION_STOP
        context.startService(intent)
        _uiState.update { it.copy(statusMessage = "⏹ Vordergrund-Dienst gestoppt") }
    }

    /** DSGVO Art. 15 – Datenauskunft als JSON (ohne Passwörter/PINs). */
    fun exportDataSubjectAccess() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { privacyService.exportDataSubjectAccess() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { exp ->
                            "✅ Datenauskunft: ${exp.file.name} " +
                                "(${exp.assetCount} Assets, ${exp.detectionCount} Detektionen)"
                        },
                        onFailure = { e -> "❌ Datenauskunft: ${e.message}" }
                    )
                )
            }
        }
    }

    /** Retention: Historie älter als 90 Tage. */
    fun applyDataRetention() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching {
                privacyService.applyRetention(
                    com.secureguard.enterprise.services.PrivacyService.DEFAULT_RETENTION_DAYS
                )
            }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { r ->
                            "✅ Retention 90 Tage: Detektionen −${r.detectionsDeleted}, " +
                                "Alerts −${r.alertsDeleted}, Audit −${r.auditDeleted}"
                        },
                        onFailure = { e -> "❌ Retention: ${e.message}" }
                    )
                )
            }
        }
    }

    /**
     * DSGVO Art. 17 – alle lokalen Nutzdaten löschen.
     * PIN bleibt, sofern [alsoClearAuth] false (Passwort setzt der Anwender selbst neu).
     */
    fun eraseAllLocalData(alsoClearAuth: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching { privacyService.eraseAllLocalData(alsoClearAuth) }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { r ->
                            "✅ Lokale Daten gelöscht (Assets −${r.assetsDeleted}, " +
                                "Detektionen −${r.detectionsDeleted})"
                        },
                        onFailure = { e -> "❌ Löschen: ${e.message}" }
                    )
                )
            }
        }
    }

    companion object {
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_CROWD = "external_crowd"
        private const val KEY_OFFLINE = "offline_only"
        private const val KEY_LEARNING = "learning_mode"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_CONSENT = "gdpr_consent"
        private const val KEY_USERNAME = "user_name"
        private const val KEY_ORG = "organization"
    }
}
