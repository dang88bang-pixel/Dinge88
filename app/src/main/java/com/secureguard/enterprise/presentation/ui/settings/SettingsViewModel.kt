package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.BuildConfig
import com.secureguard.enterprise.security.Role
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.BackupManager
import com.secureguard.enterprise.services.ExportService
import com.secureguard.enterprise.services.MqttService
import com.secureguard.enterprise.services.UsbSerialService
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
    // Profil (echte Werte, kein Platzhalter)
    val pinConfigured: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceModel: String = Build.MODEL,
    val userRole: String = "ADMIN",
    // Benachrichtigungen & Agent
    val notificationsEnabled: Boolean = true,
    val externalCrowdAllowed: Boolean = false,
    val offlineOnly: Boolean = true,
    val learningMode: Boolean = true,
    val darkMode: Boolean = false,
    val consentGiven: Boolean = true,
    val agentRunning: Boolean = false,
    // Verbindungen (echter Status)
    val mqttConnected: Boolean = false,
    val websocketConfigured: Boolean = false,
    val websocketConnected: Boolean = false,
    val mcpConfigured: Boolean = false,
    // Laufzeit-Berechtigungen
    val missingPermissions: List<String> = emptyList(),
    // Daten (Export/Backup) – Ergebnis-Meldung + Backup-Anzahl
    val dataActionMessage: String = "",
    val backupCount: Int = 0,
    val isDataActionRunning: Boolean = false,
    // USB-Diagnose
    val usbDevices: List<String> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val agentService: AgentService,
    private val mqttService: MqttService,
    private val webSocketService: WebSocketService,
    private val exportService: ExportService,
    private val backupManager: BackupManager,
    private val usbSerialService: UsbSerialService
) : ViewModel() {

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Verbindungswerte und Berechtigungen live nachführen (echter Status). */
    fun refreshConnectionStatus() {
        _uiState.update {
            it.copy(
                agentRunning = agentService.agentStatus.value.running,
                mqttConnected = mqttService.isConnected,
                websocketConfigured = webSocketService.isConfigured,
                websocketConnected = webSocketService.isConnected,
                mcpConfigured = BuildConfig.MCP_SERVER_URL.isNotBlank(),
                missingPermissions = com.secureguard.enterprise.presentation.ui.common
                    .missingPermissions(context),
                backupCount = backupManager.listBackups().size
            )
        }
    }

    private fun load() = SettingsUiState(
        pinConfigured = authManager.isPinConfigured(),
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        externalCrowdAllowed = prefs.getBoolean(KEY_CROWD, false),
        offlineOnly = prefs.getBoolean(KEY_OFFLINE, true),
        learningMode = prefs.getBoolean(KEY_LEARNING, true),
        darkMode = prefs.getBoolean(KEY_DARK, false),
        consentGiven = prefs.getBoolean(KEY_CONSENT, true),
        agentRunning = agentService.agentStatus.value.running,
        websocketConfigured = webSocketService.isConfigured,
        userRole = authManager.currentUser.value.role.name
    )

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
                .apply()
            next
        }
    }

    fun setNotifications(value: Boolean) = save { it.copy(notificationsEnabled = value) }

    fun setExternalCrowd(value: Boolean) {
        save { it.copy(externalCrowdAllowed = value) }
        // Wirkt sofort auf den laufenden Agenten (externe Quellen freigeben/sperren).
        agentService.updateSettings { it.copy(externalSources = value) }
    }

    fun setOfflineOnly(value: Boolean) {
        save { it.copy(offlineOnly = value) }
        agentService.updateSettings { it.copy(offlineOnly = value) }
    }

    fun setLearning(value: Boolean) {
        save { it.copy(learningMode = value) }
        agentService.updateSettings { it.copy(learningMode = value) }
    }

    fun setDarkMode(value: Boolean) = save { it.copy(darkMode = value) }
    fun setConsent(value: Boolean) = save { it.copy(consentGiven = value) }

    // ============ ROLLEN (RBAC) ============

    /** Wechselt die Geräte-Rolle (ADMIN/MANAGER/OPERATOR/VIEWER). */
    fun setUserRole(role: Role) {
        authManager.setCurrentRole(role)
        _uiState.update { it.copy(userRole = role.name) }
    }

    // ============ DATEN: EXPORT & BACKUP ============

    fun exportAssetsCsv() {
        viewModelScope.launch {
            setDataRunning(true)
            runCatching { exportService.exportAssetsCsv() }
                .onSuccess { file ->
                    dataMessage("Assets exportiert: ${file.absolutePath}")
                }
                .onFailure { dataMessage("Export fehlgeschlagen: ${it.message}") }
            setDataRunning(false)
        }
    }

    fun exportDetectionsCsv() {
        viewModelScope.launch {
            setDataRunning(true)
            runCatching { exportService.exportDetectionsCsv() }
                .onSuccess { file -> dataMessage("Detektionen exportiert: ${file.absolutePath}") }
                .onFailure { dataMessage("Export fehlgeschlagen: ${it.message}") }
            setDataRunning(false)
        }
    }

    fun exportEncryptedAssetsCsv() {
        viewModelScope.launch {
            setDataRunning(true)
            runCatching { exportService.exportAssetsCsvEncrypted() }
                .onSuccess { file ->
                    dataMessage("Verschlüsselter Export: ${file.absolutePath}")
                }
                .onFailure { dataMessage("Export fehlgeschlagen: ${it.message}") }
            setDataRunning(false)
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            setDataRunning(true)
            runCatching { backupManager.createBackup() }
                .onSuccess { file ->
                    dataMessage("Backup erstellt: ${file.name}")
                    refreshConnectionStatus()
                }
                .onFailure { dataMessage("Backup fehlgeschlagen: ${it.message}") }
            setDataRunning(false)
        }
    }

    /** Stellt das neueste Backup bereit (wirksam nach App-Neustart). */
    fun restoreLatestBackup() {
        viewModelScope.launch {
            setDataRunning(true)
            val latest = backupManager.listBackups().maxByOrNull { it.lastModified() }
            if (latest == null) {
                dataMessage("Kein Backup vorhanden")
            } else {
                runCatching { backupManager.stageRestore(latest) }
                    .onSuccess { ok ->
                        dataMessage(
                            if (ok) "Restore vorbereitet (${latest.name}) – beim nächsten Start aktiv"
                            else "Ungültige Backup-Datei"
                        )
                    }
                    .onFailure { dataMessage("Restore fehlgeschlagen: ${it.message}") }
            }
            setDataRunning(false)
        }
    }

    private fun setDataRunning(running: Boolean) {
        _uiState.update { it.copy(isDataActionRunning = running) }
    }

    private fun dataMessage(message: String) {
        _uiState.update { it.copy(dataActionMessage = message) }
    }

    // ============ USB-DIAGNOSE ============

    /** Listet echte USB-Serial-Adapter (FTDI/CP210x/CH34x …) auf. */
    fun refreshUsbDevices() {
        viewModelScope.launch {
            val devices = runCatching { usbSerialService.availableDrivers() }
                .getOrDefault(emptyList())
                .map { driver ->
                    val device = driver.device
                    val permission = if (usbSerialService.hasPermission(driver)) "zugang" else "gesperrt"
                    "USB ${device.vendorId.toString(16)}:${device.productId.toString(16)} · " +
                        "${driver.ports.size} Port(s) · $permission"
                }
            _uiState.update { it.copy(usbDevices = devices) }
        }
    }

    companion object {
        private const val KEY_NOTIFICATIONS = "notifications"
        private const val KEY_CROWD = "external_crowd"
        private const val KEY_OFFLINE = "offline_only"
        private const val KEY_LEARNING = "learning_mode"
        private const val KEY_DARK = "dark_mode"
        private const val KEY_CONSENT = "gdpr_consent"
    }
}

