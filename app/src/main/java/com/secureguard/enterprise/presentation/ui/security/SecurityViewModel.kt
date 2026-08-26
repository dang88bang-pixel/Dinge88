package com.secureguard.enterprise.presentation.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.security.DatabaseKeyManager
import com.secureguard.enterprise.services.AuthManager
import com.secureguard.enterprise.services.AuditLogService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val auditLogService: AuditLogService,
    private val encryptionService: com.secureguard.enterprise.services.EncryptionService,
    private val satelliteService: com.secureguard.enterprise.services.SatelliteService,
    private val nfcService: com.secureguard.enterprise.services.NfcService,
    private val databaseKeyManager: DatabaseKeyManager
) : ViewModel() {

    val dbKeyFingerprint: String = runCatching { databaseKeyManager.passphraseFingerprint() }.getOrDefault("–")

    private val _auditEntries = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditEntries: StateFlow<List<AuditLog>> = _auditEntries.asStateFlow()

    private val _pinConfigured = MutableStateFlow(authManager.isPinConfigured())
    val pinConfigured: StateFlow<Boolean> = _pinConfigured.asStateFlow()

    val authState = authManager.state

    fun loadAuditLog() {
        viewModelScope.launch {
            _auditEntries.value = auditLogService.latest(100)
            _pinConfigured.value = authManager.isPinConfigured()
        }
    }

    fun configurePin(pin: String) {
        if (authManager.configurePin(pin)) {
            _pinConfigured.value = true
            viewModelScope.launch {
                auditLogService.log("PIN_CHANGE", "PIN geändert über Security-Center")
                loadAuditLog()
            }
        }
    }

    fun disablePin() {
        authManager.disablePin()
        _pinConfigured.value = false
        viewModelScope.launch {
            auditLogService.log("PIN_DISABLE", "PIN entfernt über Security-Center")
            loadAuditLog()
        }
    }

    fun lockApp() {
        authManager.lock()
        viewModelScope.launch {
            auditLogService.log("APP_LOCK", "App manuell gesperrt über Security-Center")
        }
    }

    fun clearAuditLog() {
        viewModelScope.launch {
            auditLogService.clear()
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
        _encryptionTestResult.value = if (match) "✅ AES/GCM Roundtrip OK" else "❌ Entschlüsselung fehlgeschlagen"
    }

    // GPS location
    private val _gpsLocation = MutableStateFlow<String?>(null)
    val gpsLocation: StateFlow<String?> = _gpsLocation.asStateFlow()

    fun fetchGpsLocation() {
        viewModelScope.launch {
            val location = satelliteService.currentLocation()
            _gpsLocation.value = if (location != null) {
                "📍 ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)} (±${"%.0f".format(location.accuracy)}m)"
            } else {
                "❌ Kein GPS-Fix verfügbar"
            }
        }
    }

    // NFC status
    val nfcAvailable: Boolean = nfcService.isAvailable()
}
