package com.secureguard.enterprise.ct45p

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.util.ActivityStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** UI-Zustand der CT45P-Statusleiste (Sichtbarkeit auf dem Gerät). */
data class CT45PStatusUiState(
    val agentRunning: Boolean = false,
    val activeNodes: Int = 0,
    val batteryLevel: Int = 100,
    val lastAction: String = "Keine",
    val lastActionTarget: String = "-",
    val lastActionSuccess: Boolean = true,
    val lastActionTime: String = "--:--:--",
    val lastRequestSource: String = "-",
    val lastRequestDuration: String = "-",
    val recentEntries: List<AuditLog> = emptyList(),
    val stats: ActivityStatsCalculator.ActivityStats = ActivityStatsCalculator.ActivityStats()
)

/**
 * Belebt die CT45P-Statusleiste: Agent-Status, Anzahl verfolgter Knoten
 * (Assets), letzte Aktion (Audit-Log), letzte Anfrage (on-device Log-Datei)
 * und das Echtzeit-Aktivitätslog.
 */
@HiltViewModel
class CT45PStatusViewModel @Inject constructor(
    private val agentService: AgentService,
    private val repository: SecureGuardRepository,
    private val auditLogService: AuditLogService,
    private val logManager: CT45PLogManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CT45PStatusUiState())
    val uiState: StateFlow<CT45PStatusUiState> = _uiState.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        monitorAgent()
        monitorAuditLog()
    }

    /** Agent-Status + Anzahl der verfolgten Assets ("Knoten"). */
    private fun monitorAgent() {
        viewModelScope.launch {
            combine(agentService.agentStatus, repository.getWhitelistedAssets()) { status, assets ->
                status.running to assets.size
            }.collect { (running, nodes) ->
                _uiState.update { state ->
                    state.copy(agentRunning = running, activeNodes = nodes)
                }
            }
        }
    }

    /**
     * Bei jedem neuen Audit-Eintrag wird zusätzlich die CT45P-Log-Datei
     * eingelesen; daraus werden "Letzte Anfrage", "Letzte Aktion" und die
     * 24h-Statistik abgeleitet (Echtzeit-Log im UI).
     */
    private fun monitorAuditLog() {
        viewModelScope.launch {
            auditLogService.entries.collect { entries ->
                val parsed = logManager.parseTail(20)
                val lastActionEntry = entries.firstOrNull { it.action.startsWith("ACTION") }
                val lastRequest = parsed.lastOrNull {
                    it.requestType.startsWith("SEARCH", ignoreCase = true) ||
                        it.requestType.startsWith("BLE", ignoreCase = true) ||
                        it.requestType.startsWith("LORA", ignoreCase = true) ||
                        it.requestType.startsWith("GPS", ignoreCase = true)
                }

                _uiState.update { state ->
                    state.copy(
                        recentEntries = entries.take(5),
                        lastAction = lastActionEntry?.details
                            ?.substringBefore("→")?.trim()
                            ?.ifEmpty { null } ?: state.lastAction,
                        lastActionTarget = lastActionEntry?.details
                            ?.let { d ->
                                if (d.contains("→")) d.substringAfter("→").substringBefore("(").trim()
                                else d
                            } ?: state.lastActionTarget,
                        lastActionSuccess = lastActionEntry?.let {
                            !it.details.contains("success=false", ignoreCase = true)
                        } ?: state.lastActionSuccess,
                        lastActionTime = lastActionEntry?.let {
                            timeFormat.format(Date(it.timestamp))
                        } ?: state.lastActionTime,
                        lastRequestSource = lastRequest?.requestType ?: state.lastRequestSource,
                        lastRequestDuration = lastRequest?.let { "${it.durationMs}ms" }
                            ?: state.lastRequestDuration,
                        stats = ActivityStatsCalculator.calculate(logManager)
                    )
                }
            }
        }
    }
}
