package com.secureguard.enterprise.presentation.ui.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.R
import com.secureguard.enterprise.presentation.theme.TerminalAmber
import com.secureguard.enterprise.presentation.theme.TerminalCyan
import com.secureguard.enterprise.presentation.theme.TerminalGreen
import com.secureguard.enterprise.presentation.theme.TerminalRed
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AgentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
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
                addEntry(context.getString(R.string.term_agent_started), TerminalGreen)
            }
            "stop" -> {
                agentService.stop()
                addEntry(context.getString(R.string.term_agent_stopped), TerminalAmber)
            }
            "cycle" -> {
                addEntry(context.getString(R.string.term_cycle_manual), TerminalCyan)
                viewModelScope.launch {
                    val result = agentService.runCycle()
                    addEntry(context.getString(R.string.term_cycle_result, result.assetsChecked, result.detections), TerminalGreen)
                    result.channelHits.forEach { (channel, count) ->
                        addEntry("    $channel: $count", TerminalCyan)
                    }
                }
            }
            "flush" -> {
                viewModelScope.launch {
                    val flushed = agentService.flushOfflineQueue()
                    addEntry(context.getString(R.string.term_flush_result, flushed), TerminalGreen)
                }
            }
            "help" -> {
                addEntry(context.getString(R.string.term_help_header), TerminalCyan)
                addEntry(context.getString(R.string.term_help_status), TerminalGreen)
                addEntry(context.getString(R.string.term_help_start), TerminalGreen)
                addEntry(context.getString(R.string.term_help_stop), TerminalGreen)
                addEntry(context.getString(R.string.term_help_cycle), TerminalGreen)
                addEntry(context.getString(R.string.term_help_flush), TerminalGreen)
                addEntry(context.getString(R.string.term_help_clear), TerminalGreen)
                addEntry(context.getString(R.string.term_help_self), TerminalGreen)
            }
            "clear" -> {
                _logEntries.value = emptyList()
            }
            else -> {
                addEntry(context.getString(R.string.term_unknown_cmd, command), TerminalRed)
                addEntry(context.getString(R.string.term_hint_help), TerminalAmber)
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
