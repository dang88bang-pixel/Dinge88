package com.secureguard.enterprise.presentation.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.secureguard.enterprise.services.AgentForegroundService
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentService: AgentService,
    private val backupManager: com.secureguard.enterprise.services.BackupManager,
    private val exportService: com.secureguard.enterprise.services.ExportService
) : ViewModel() {

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(load())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun load() = SettingsUiState(
        notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true),
        externalCrowdAllowed = prefs.getBoolean(KEY_CROWD, false),
        offlineOnly = prefs.getBoolean(KEY_OFFLINE, true),
        learningMode = prefs.getBoolean(KEY_LEARNING, true),
        darkMode = prefs.getBoolean(KEY_DARK, false),
        consentGiven = prefs.getBoolean(KEY_CONSENT, true),
        userName = prefs.getString(KEY_USERNAME, "Admin") ?: "Admin",
        organization = prefs.getString(KEY_ORG, "SecureGuard") ?: "SecureGuard"
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
                .putString(KEY_USERNAME, next.userName)
                .putString(KEY_ORG, next.organization)
                .apply()

            // Restart agent with new settings if running
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

    fun clearStatus() { _uiState.update { it.copy(statusMessage = null) } }

    fun createBackup() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = runCatching { backupManager.createBackup() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> context.getString(com.secureguard.enterprise.R.string.export_backup_success, file.name) },
                        onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.export_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    fun exportCsv() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = runCatching { exportService.exportAssetsCsv() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> context.getString(com.secureguard.enterprise.R.string.export_csv_success, file.name) },
                        onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.export_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    fun exportPdf() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = runCatching {
                exportService.exportPdfReport(emptyList(), emptyList())
            }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> context.getString(com.secureguard.enterprise.R.string.export_pdf_success, file.name) },
                        onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.export_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    fun exportDetectionsCsv() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = runCatching { exportService.exportDetectionsCsv() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> context.getString(com.secureguard.enterprise.R.string.export_detections_success, file.name) },
                        onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.export_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    fun exportEncryptedCsv() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val result = runCatching { exportService.exportAssetsCsvEncrypted() }
            _uiState.update {
                it.copy(
                    statusMessage = result.fold(
                        onSuccess = { file -> context.getString(com.secureguard.enterprise.R.string.export_encrypted_success, file.name) },
                        onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.export_failed, e.message ?: "") }
                    )
                )
            }
        }
    }

    fun getOfflineMapUrl(): String {
        val offlineMapService = com.secureguard.enterprise.services.OfflineMapService()
        return offlineMapService.downloadRegionUrl()
    }

    fun restoreBackup() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val backups = backupManager.listBackups()
            val latest = backups.firstOrNull()
            if (latest != null) {
                val result = runCatching { backupManager.stageRestore(latest) }
                _uiState.update {
                    it.copy(
                        statusMessage = result.fold(
                            onSuccess = { ok -> if (ok) context.getString(com.secureguard.enterprise.R.string.restore_prepared, latest.name) else context.getString(com.secureguard.enterprise.R.string.restore_failed) },
                            onFailure = { e -> context.getString(com.secureguard.enterprise.R.string.restore_error, e.message ?: "") }
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = context.getString(com.secureguard.enterprise.R.string.no_backups)) }
            }
        }
    }

    fun listBackups(): Int = backupManager.listBackups().size

    fun startForegroundService() {
        val intent = android.content.Intent(context, AgentForegroundService::class.java)
        intent.action = AgentForegroundService.ACTION_START
        androidx.core.content.ContextCompat.startForegroundService(context, intent)
        _uiState.update { it.copy(statusMessage = context.getString(com.secureguard.enterprise.R.string.fg_service_started)) }
    }

    fun stopForegroundService() {
        val intent = android.content.Intent(context, AgentForegroundService::class.java)
        intent.action = AgentForegroundService.ACTION_STOP
        context.startService(intent)
        _uiState.update { it.copy(statusMessage = context.getString(com.secureguard.enterprise.R.string.fg_service_stopped)) }
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
