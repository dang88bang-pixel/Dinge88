package com.secureguard.enterprise.presentation.ui.tempmail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.R
import com.secureguard.enterprise.services.TempMailService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
            addLog(context.getString(R.string.tm_creating_inbox))
            val result = tempMailService.createInbox()
            if (result?.success == true) {
                addLog(context.getString(R.string.tm_inbox_created, result.email))
            } else {
                addLog(context.getString(R.string.tm_inbox_create_failed))
            }
        }
    }

    fun waitForOTP() {
        viewModelScope.launch {
            addLog(context.getString(R.string.tm_waiting_otp))
            val result = tempMailService.waitForOTP()
            if (result?.success == true) {
                addLog(context.getString(R.string.tm_otp_received, result.otp))
                addLog(context.getString(R.string.tm_otp_from_subject, result.from, result.subject))
            } else {
                addLog(context.getString(R.string.tm_no_otp_timeout))
            }
        }
    }

    fun clearInbox() {
        tempMailService.clearInbox()
        addLog(context.getString(R.string.tm_inbox_cleared))
    }

    fun checkExistingOtp() {
        viewModelScope.launch {
            val otp = tempMailService.getOTP()
            if (otp != null) {
                addLog(context.getString(R.string.tm_last_otp, otp.otp, otp.email))
            } else {
                addLog(context.getString(R.string.tm_no_saved_otp))
            }
        }
    }

    fun waitForMagicLink() {
        viewModelScope.launch {
            addLog(context.getString(R.string.tm_waiting_magic_link))
            val result = tempMailService.waitForMagicLink()
            if (result?.success == true) {
                addLog(context.getString(R.string.tm_magic_link, result.magicLink.take(60)))
            } else {
                addLog(context.getString(R.string.tm_no_magic_link))
            }
        }
    }

    fun quickRegister() {
        viewModelScope.launch {
            addLog(context.getString(R.string.tm_quick_register))
            val result = tempMailService.autoRegisterAndGetOTP()
            if (result?.success == true) {
                addLog(context.getString(R.string.tm_otp_is, result.otp))
            } else {
                addLog(context.getString(R.string.tm_quick_register_failed))
            }
        }
    }

    fun autoRegisterService(serviceName: String, url: String) {
        viewModelScope.launch {
            addLog(context.getString(R.string.tm_auto_register, serviceName))
            // Uses AgentService.autoRegisterExternalService indirectly via TempMailService
            val inbox = tempMailService.createInbox()
            if (inbox?.success == true) {
                addLog(context.getString(R.string.tm_inbox_created, inbox.email))
                addLog(context.getString(R.string.tm_waiting_otp_short))
                val otp = tempMailService.waitForOTP()
                if (otp?.success == true) {
                    addLog("✅ OTP empfangen: ${otp.otp}")
                } else {
                    addLog(context.getString(R.string.tm_no_otp_timeout))
                }
            } else {
                addLog(context.getString(R.string.tm_inbox_create_failed))
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
