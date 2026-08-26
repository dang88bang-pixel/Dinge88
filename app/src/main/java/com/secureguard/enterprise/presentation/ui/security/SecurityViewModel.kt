package com.secureguard.enterprise.presentation.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.secureguard.enterprise.R
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager,
    private val auditLogService: AuditLogService,
    private val encryptionService: com.secureguard.enterprise.services.EncryptionService,
    private val satelliteService: com.secureguard.enterprise.services.SatelliteService,
    private val nfcService: com.secureguard.enterprise.services.NfcService
) : ViewModel() {

    private val _auditEntries = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditEntries: StateFlow<List<AuditLog>> = _auditEntries.asStateFlow()

    val pinConfigured: StateFlow<Boolean> = MutableStateFlow(authManager.isPinConfigured()).also {
        viewModelScope.launch { it.value = authManager.isPinConfigured() }
    }

    val authState = authManager.state

    fun loadAuditLog() {
        viewModelScope.launch {
            _auditEntries.value = auditLogService.latest(100)
        }
    }

    fun configurePin(pin: String) {
        authManager.configurePin(pin)
        viewModelScope.launch {
            auditLogService.log("PIN_CHANGE", context.getString(R.string.audit_pin_change))
            loadAuditLog()
        }
    }

    fun disablePin() {
        authManager.disablePin()
        viewModelScope.launch {
            auditLogService.log("PIN_DISABLE", context.getString(R.string.audit_pin_disable))
            loadAuditLog()
        }
    }

    fun lockApp() {
        authManager.lock()
        viewModelScope.launch {
            auditLogService.log("APP_LOCK", context.getString(R.string.audit_app_lock))
        }
    }

    fun clearAuditLog() {
        viewModelScope.launch {
            auditLogService.log("AUDIT_CLEAR", context.getString(R.string.audit_cleared))
            _auditEntries.value = emptyList()
        }
    }

    // Encryption test
    private val _encryptionTestResult = MutableStateFlow<String?>(null)
    val encryptionTestResult: StateFlow<String?> = _encryptionTestResult.asStateFlow()

    fun testEncryption() {
        val testData = "SecureGuard Test ${System.currentTimeMillis()}"
        val encrypted = encryptionService.encrypt(testData.toByteArray())
        val decrypted = encryptionService.decrypt(encrypted.data, encrypted.iv)
        val match = String(decrypted) == testData
        _encryptionTestResult.value = if (match) context.getString(R.string.encryption_ok) else context.getString(R.string.encryption_failed)
    }

    // GPS location
    private val _gpsLocation = MutableStateFlow<String?>(null)
    val gpsLocation: StateFlow<String?> = _gpsLocation.asStateFlow()

    fun fetchGpsLocation() {
        viewModelScope.launch {
            val location = satelliteService.currentLocation()
            _gpsLocation.value = if (location != null) {
                context.getString(
                    R.string.gps_location_format,
                    "%.5f".format(location.latitude),
                    "%.5f".format(location.longitude),
                    "%.0f".format(location.accuracy)
                )
            } else {
                context.getString(R.string.gps_no_fix)
            }
        }
    }

    // NFC status
    val nfcAvailable: Boolean = nfcService.isAvailable()
}
