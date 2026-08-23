package com.secureguard.enterprise.presentation.ui.tempmail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.TempMailService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TempMailViewModel @Inject constructor(
    private val tempMailService: TempMailService
) : ViewModel() {

    private val _uiState = MutableStateFlow(TempMailUiState())
    val uiState: StateFlow<TempMailUiState> = _uiState.asStateFlow()

    val currentInbox = tempMailService.currentInbox
    val lastOTP = tempMailService.lastOTP
    val isProcessing = tempMailService.isProcessing
    val isConfigured = tempMailService.isConfigured

    fun createInbox() {
        viewModelScope.launch {
            addLog("📬 Erstelle neue Inbox…")
            val result = tempMailService.createInbox()
            if (result?.success == true) {
                addLog("✅ Inbox erstellt: ${result.email}")
            } else {
                addLog("❌ Fehler beim Erstellen der Inbox (MCP-Server konfiguriert?)")
            }
        }
    }

    fun waitForOTP() {
        viewModelScope.launch {
            addLog("⏳ Warte auf OTP (bis 45 s)…")
            val result = tempMailService.waitForOTP()
            if (result?.success == true) {
                addLog("✅ OTP empfangen: ${result.otp}")
                addLog("📧 Von: ${result.from} · Betreff: ${result.subject}")
            } else {
                addLog("❌ Kein OTP empfangen (Timeout?)")
            }
        }
    }

    fun clearInbox() {
        tempMailService.clearInbox()
        addLog("🗑️ Inbox geleert")
    }

    fun checkExistingOtp() {
        viewModelScope.launch {
            val otp = tempMailService.getOTP()
            if (otp != null) {
                addLog("📋 Letztes OTP: ${otp.otp} (${otp.email})")
            } else {
                addLog("ℹ️ Kein gespeichertes OTP vorhanden")
            }
        }
    }

    fun waitForMagicLink() {
        viewModelScope.launch {
            addLog("⏳ Warte auf Magic Link (45s)...")
            val result = tempMailService.waitForMagicLink()
            if (result?.success == true) {
                addLog("✅ Magic Link: ${result.magicLink.take(60)}...")
            } else {
                addLog("❌ Kein Magic Link empfangen")
            }
        }
    }

    fun quickRegister() {
        viewModelScope.launch {
            addLog("🔄 Quick-Register: Inbox + OTP...")
            val result = tempMailService.autoRegisterAndGetOTP()
            if (result?.success == true) {
                addLog("✅ OTP: ${result.otp}")
            } else {
                addLog("❌ Quick-Register fehlgeschlagen")
            }
        }
    }

    fun autoRegisterService(serviceName: String, url: String) {
        viewModelScope.launch {
            addLog("🌐 Auto-Register bei $serviceName...")
            // Uses AgentService.autoRegisterExternalService indirectly via TempMailService
            val inbox = tempMailService.createInbox()
            if (inbox?.success == true) {
                addLog("✅ Inbox: ${inbox.email}")
                addLog("⏳ Warte auf OTP...")
                val otp = tempMailService.waitForOTP()
                if (otp?.success == true) {
                    addLog("✅ OTP empfangen: ${otp.otp}")
                } else {
                    addLog("❌ Kein OTP (Timeout)")
                }
            } else {
                addLog("❌ Inbox-Erstellung fehlgeschlagen")
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $message"
        _uiState.update { state ->
            state.copy(logEntries = state.logEntries + entry)
        }
    }
}

data class TempMailUiState(
    val logEntries: List<String> = emptyList()
)
