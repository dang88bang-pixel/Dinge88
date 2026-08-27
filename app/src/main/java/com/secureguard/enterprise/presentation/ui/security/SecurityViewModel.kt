package com.secureguard.enterprise.presentation.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.security.DatabaseKeyManager
import com.secureguard.enterprise.security.Permission
import com.secureguard.enterprise.security.Role
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
    private val databaseKeyManager: DatabaseKeyManager,
    private val roleManager: com.secureguard.enterprise.security.RoleManager
) : ViewModel() {

    val dbKeyFingerprint: String = runCatching { databaseKeyManager.passphraseFingerprint() }.getOrDefault("–")

    private val _auditEntries = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditEntries: StateFlow<List<AuditLog>> = _auditEntries.asStateFlow()

    private val _pinConfigured = MutableStateFlow(authManager.isPinConfigured())
    val pinConfigured: StateFlow<Boolean> = _pinConfigured.asStateFlow()

    val authState = authManager.state

    /** Aktive RBAC-Rolle (F-44) + Wechselrecht (MANAGE_USERS). */
    val role: StateFlow<Role> = roleManager.role
    val canSwitchRoles: Boolean = roleManager.has(Permission.MANAGE_USERS)

    /** Rollenwechsel (Security-Center); verweigert ohne MANAGE_USERS. */
    fun setRole(newRole: Role) {
        if (!roleManager.has(Permission.MANAGE_USERS)) {
            viewModelScope.launch {
                auditLogService.log(
                    action = "ROLE_SWITCH_DENIED",
                    details = "Rolle ${roleManager.currentRole} darf nicht wechseln"
                )
            }
            return
        }
        roleManager.setRole(newRole)
        viewModelScope.launch {
            auditLogService.log(action = "ROLE_SWITCH", details = "Neue Rolle: $newRole")
        }
    }

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

    /** Auto-Lock-Dauer setzen (F-49): 5/10/30 Minuten. Sichtbar über [authState]. */
    fun setAutoLockMinutes(minutes: Int) {
        authManager.setAutoLockMinutes(minutes)
        viewModelScope.launch {
            auditLogService.log(action = "AUTO_LOCK_SET", details = "Auto-Lock: $minutes Min")
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
