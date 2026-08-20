package com.secureguard.enterprise.services

import com.secureguard.enterprise.mcp.InboxResult
import com.secureguard.enterprise.mcp.MCPClient
import com.secureguard.enterprise.mcp.MagicLinkResult
import com.secureguard.enterprise.mcp.OTPResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporäre E-Mail-Dienste für den Agenten (OTP-Empfang, Magic Links).
 *
 * Kapselt den [MCPClient] und bietet einen einfachen, zustandsbehafteten
 * Workflow: Inbox erstellen → auf OTP warten → Ergebnis abrufen.
 *
 * WICHTIG: Nur für legitime Zwecke (firmeninterne Testumgebungen,
 * autorisierte API-Key-Generierung, QA/E2E-Testkonten). Ohne konfigurierten
 * MCP-Server liefern alle Aufrufe `null` – die App bleibt stabil.
 */
@Singleton
class TempMailService @Inject constructor(
    private val mcpClient: MCPClient
) {

    private val _currentInbox = MutableStateFlow<InboxResult?>(null)
    val currentInbox: StateFlow<InboxResult?> = _currentInbox.asStateFlow()

    private val _lastOTP = MutableStateFlow<OTPResult?>(null)
    val lastOTP: StateFlow<OTPResult?> = _lastOTP.asStateFlow()

    private val _lastMagicLink = MutableStateFlow<MagicLinkResult?>(null)
    val lastMagicLink: StateFlow<MagicLinkResult?> = _lastMagicLink.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    val isConfigured: Boolean get() = mcpClient.isConfigured

    // ============ INBOX VERWALTUNG ============

    /** Erstellt eine neue temporäre Inbox. */
    suspend fun createInbox(): InboxResult? {
        _isProcessing.value = true
        return try {
            val result = mcpClient.createInbox()
            if (result?.success == true) {
                _currentInbox.value = result
            }
            result
        } catch (e: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Wartet auf eine OTP-E-Mail für die aktuelle Inbox (Long-Polling). */
    suspend fun waitForOTP(timeoutMs: Long = 45_000L): OTPResult? {
        val inbox = _currentInbox.value ?: return null
        _isProcessing.value = true
        return try {
            val result = mcpClient.waitForOTP(inbox.token, timeoutMs)
            if (result?.success == true) {
                _lastOTP.value = result
            }
            result
        } catch (e: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Extrahiert einen Magic Link aus einer eingegangenen E-Mail. */
    suspend fun waitForMagicLink(timeoutMs: Long = 45_000L): MagicLinkResult? {
        val inbox = _currentInbox.value ?: return null
        _isProcessing.value = true
        return try {
            val result = mcpClient.extractMagicLink(inbox.token)
            if (result?.success == true) {
                _lastMagicLink.value = result
            }
            result
        } catch (e: Exception) {
            null
        } finally {
            _isProcessing.value = false
        }
    }

    /** Letztes OTP-Ergebnis (ohne neuen Abruf). */
    suspend fun getOTP(): OTPResult? = _lastOTP.value

    /** Setzt Inbox und Ergebnisse zurück. */
    fun clearInbox() {
        _currentInbox.value = null
        _lastOTP.value = null
        _lastMagicLink.value = null
        _isProcessing.value = false
        mcpClient.disconnect()
    }

    // ============ AUTOMATISIERTER FLOW ============

    /**
     * Kompletter Workflow für den Agenten: Inbox erstellen und auf OTP
     * warten. Die E-Mail-Adresse muss vom Aufrufer für die Registrierung
     * verwendet werden (die Wartezeit läuft ab Inbox-Erstellung).
     */
    suspend fun autoRegisterAndGetOTP(timeoutMs: Long = 45_000L): OTPResult? {
        val inbox = createInbox() ?: return null
        return waitForOTP(timeoutMs)
    }
}
