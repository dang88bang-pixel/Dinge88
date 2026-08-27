package com.secureguard.enterprise.presentation.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.secureguard.enterprise.presentation.theme.TerminalAmber
import com.secureguard.enterprise.presentation.theme.TerminalCyan
import com.secureguard.enterprise.presentation.theme.TerminalGreen
import com.secureguard.enterprise.presentation.theme.TerminalRed
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.compose.ui.graphics.Color

data class TerminalEntry(
    val text: String,
    val color: Color,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val agentService: AgentService
) : ViewModel() {

    private val _logEntries = MutableStateFlow<List<TerminalEntry>>(emptyList())
    val logEntries: StateFlow<List<TerminalEntry>> = _logEntries.asStateFlow()

    val agentStatus: StateFlow<AgentStatus> = agentService.agentStatus

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        addEntry("# System initialized. SecureGuard Core v1.0 active.", TerminalGreen)
        addEntry("# Loading diagnostic modules... Done.", TerminalGreen)
        addEntry("# Agent status: ${if (agentService.agentStatus.value.running) "RUNNING" else "STOPPED"}", TerminalCyan)
        addEntry("# ${agentService.agentStatus.value.cycle} cycles completed.", TerminalCyan)
        addEntry("", TerminalGreen)
    }

    fun executeCommand(command: String) {
        addEntry("> $command", TerminalGreen)

        when (command.trim().lowercase()) {
            "status" -> {
                val s = agentService.agentStatus.value
                addEntry("  Agent: ${if (s.running) "RUNNING" else "STOPPED"}", TerminalCyan)
                addEntry("  Cycle: ${s.cycle} | Detections: ${s.detectionsThisCycle}", TerminalCyan)
                addEntry("  Interval: ${s.settings.interval}s | Learning: ${s.settings.learningMode}", TerminalCyan)
                addEntry("  Uptime: ${formatUptime(s.uptimeMillis)}", TerminalCyan)
            }
            "start" -> {
                agentService.start()
                addEntry("  [OK] Agent gestartet.", TerminalGreen)
            }
            "stop" -> {
                agentService.stop()
                addEntry("  [OK] Agent gestoppt.", TerminalAmber)
            }
            "cycle" -> {
                addEntry("  [INFO] Manueller Cycle gestartet...", TerminalCyan)
                // Netzwerk + DB: nicht auf Main ausführen (F-02-Rest)
                viewModelScope.launch(Dispatchers.IO) {
                    val result = agentService.runCycle()
                    addEntry("  [OK] ${result.assetsChecked} Assets | ${result.detections} Treffer", TerminalGreen)
                    result.channelHits.forEach { (channel, count) ->
                        addEntry("    $channel: $count", TerminalCyan)
                    }
                }
            }
            "flush" -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val flushed = agentService.flushOfflineQueue()
                    addEntry("  [OK] $flushed Aktionen zugestellt.", TerminalGreen)
                }
            }
            "help" -> {
                addEntry("  Befehle:", TerminalCyan)
                addEntry("    status  - Agent-Status anzeigen", TerminalGreen)
                addEntry("    start   - Agent starten", TerminalGreen)
                addEntry("    stop    - Agent stoppen", TerminalGreen)
                addEntry("    cycle   - Manueller Suchzyklus", TerminalGreen)
                addEntry("    flush   - Offline-Queue zustellen", TerminalGreen)
                addEntry("    clear   - Terminal leeren", TerminalGreen)
                addEntry("    help    - Diese Hilfe", TerminalGreen)
            }
            "clear" -> {
                _logEntries.value = emptyList()
            }
            else -> {
                addEntry("  [ERR] Unbekannter Befehl: $command", TerminalRed)
                addEntry("  Tippe 'help' für verfügbare Befehle.", TerminalAmber)
            }
        }
    }

    private fun addEntry(text: String, color: Color) {
        val prefix = if (text.startsWith("#") || text.startsWith(">") || text.isEmpty()) "" else "[${timeFormat.format(Date())}] "
        _logEntries.value = _logEntries.value + TerminalEntry("$prefix$text", color)
    }

    private fun formatUptime(ms: Long): String {
        if (ms <= 0) return "0m"
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }
}
