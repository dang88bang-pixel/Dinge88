package com.secureguard.enterprise.presentation.ui.slack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.services.SlackService
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

/**
 * ViewModel für den Slack-Screen: Status, Tools, Channels, Versand.
 *
 * Alle Aufrufe laufen über [SlackService] → Backend `/api/slack/*` →
 * Slack-MCP-Server. Ohne konfiguriertes Backend bleibt die UI leer, stürzt
 * aber nicht ab.
 */
@HiltViewModel
class SlackViewModel @Inject constructor(
    private val slackService: SlackService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SlackUiState())
    val uiState: StateFlow<SlackUiState> = _uiState.asStateFlow()

    val health = slackService.health
    val isProcessing = slackService.isProcessing
    val isConfigured = slackService.isConfigured

    init {
        refreshStatus()
    }

    /** Holt /api/slack/health inkl. Live-Probe des MCP-Servers. */
    fun refreshStatus() {
        viewModelScope.launch {
            if (!isConfigured) {
                addLog("⚠️ Kein Backend konfiguriert (Einstellungen → Backend & Broker)")
                return@launch
            }
            addLog("🔎 Frage Slack-Status ab…")
            val status = slackService.fetchHealth()
            when {
                status == null -> addLog("❌ /api/slack/health nicht erreichbar")
                !status.configured ->
                    addLog("ℹ️ Slack nicht konfiguriert (Backend: SLACK_MCP_URL)")
                status.reachable == true -> addLog(
                    "✅ Slack-MCP erreichbar – ${status.tools} Tools " +
                        "(${status.serverName} ${status.serverVersion})"
                )
                else -> addLog("⚠️ Slack-MCP nicht erreichbar: ${status.error ?: "unbekannt"}")
            }
        }
    }

    fun loadTools() {
        viewModelScope.launch {
            addLog("🧰 Lade MCP-Tools…")
            val tools = slackService.fetchTools(refresh = true)
            _uiState.update { it.copy(tools = tools) }
            if (tools.isEmpty()) {
                addLog("❌ Keine Tools erhalten (Server erreichbar?)")
            } else {
                addLog("✅ ${tools.size} Tools: ${tools.take(5).joinToString { t -> t.name }}…")
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            addLog("📋 Lade Channel-Verzeichnis…")
            val channels = slackService.fetchChannels()
            _uiState.update { it.copy(channels = channels) }
            if (channels.isEmpty()) {
                addLog("❌ Keine Channels (Cache/Token prüfen – docs/SLACK_MCP.md)")
            } else {
                addLog("✅ ${channels.size} Channels geladen")
            }
        }
    }

    fun selectChannel(channel: SlackService.SlackChannel) {
        _uiState.update { it.copy(targetChannel = channel.name.ifBlank { channel.id }) }
        addLog("🎯 Ziel-Channel: ${channel.name.ifBlank { channel.id }}")
    }

    fun sendDraft() {
        val text = _uiState.value.draft.trim()
        val target = _uiState.value.targetChannel.trim().ifBlank { null }
        if (text.isEmpty()) {
            addLog("⚠️ Keine Nachricht eingegeben")
            return
        }
        viewModelScope.launch {
            addLog("📤 Sende an ${target ?: "Default-Channel"}…")
            val result = slackService.notify(message = text, channel = target)
            when {
                result == null -> addLog("❌ Versand fehlgeschlagen (Backend/API-Key?)")
                result.ok -> {
                    addLog("✅ Gesendet über ${result.transport} → ${result.channel}")
                    _uiState.update { it.copy(draft = "") }
                }
                else -> addLog("❌ Nicht gesendet: ${result.detail.ifBlank { "unbekannt" }}")
            }
        }
    }

    fun sendTestMessage() {
        viewModelScope.launch {
            addLog("🧪 Sende Testmeldung…")
            val result = slackService.sendTestMessage()
            when {
                result == null -> addLog("❌ Testmeldung fehlgeschlagen")
                result.ok -> addLog("✅ Testmeldung angekommen (${result.channel})")
                else -> addLog("❌ ${result.detail.ifBlank { "unbekannt" }}")
            }
        }
    }

    /** Liest die letzte History eines Channels – zeigt, dass Lesen funktioniert. */
    fun loadHistory(channel: SlackService.SlackChannel) {
        viewModelScope.launch {
            val target = channel.name.ifBlank { channel.id }
            addLog("📖 Lese History von $target…")
            val result = slackService.callTool(
                tool = "conversations_history",
                arguments = mapOf("channel_id" to target, "limit" to "10")
            )
            when {
                result == null -> addLog("❌ Aufruf fehlgeschlagen")
                result.ok -> addLog("✅ History: ${result.text.take(120)}")
                else -> addLog("❌ ${result.error ?: result.text}".take(160))
            }
        }
    }

    fun updateDraft(value: String) = _uiState.update { it.copy(draft = value) }

    fun updateTargetChannel(value: String) = _uiState.update { it.copy(targetChannel = value) }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _uiState.update { state ->
            state.copy(logEntries = (state.logEntries + "[$timestamp] $message").takeLast(200))
        }
    }
}

data class SlackUiState(
    val logEntries: List<String> = emptyList(),
    val tools: List<SlackService.SlackTool> = emptyList(),
    val channels: List<SlackService.SlackChannel> = emptyList(),
    val draft: String = "",
    val targetChannel: String = ""
)
