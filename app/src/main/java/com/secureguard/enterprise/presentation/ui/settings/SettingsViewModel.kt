package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import com.secureguard.enterprise.services.BackendSyncService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.WebSocketService
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
    val findMyProxyUrl: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentService: AgentService,
    private val backupManager: com.secureguard.enterprise.services.BackupManager,
    private val exportService: com.secureguard.enterprise.services.ExportService,
    private val offlineMapService: com.secureguard.enterprise.services.OfflineMapService,
    private val endpointConfig: EndpointConfig,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val backendSyncService: BackendSyncService
) : ViewModel() {

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
            findMyProxyUrl = ep.findMyProxyUrl
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
                agentService.start(
                    AgentSettings(
                        interval = agentService.agentStatus.value.settings.interval,
                        dynamicPriority = agentService.agentStatus.value.settings.dynamicPriority,
                        learningMode = next.learningMode,
                        offlineOnly = next.offlineOnly,
                        externalSources = next.externalCrowdAllowed
                    )
                )
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

    /** Speichert Endpunkte und reconnectet MQTT/WebSocket. */
    fun saveEndpoints() {
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
            findMyUrl = s.findMyProxyUrl
        )
        // Apply live
        mqttService.reconnect()
        webSocketService.reconnect()
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
