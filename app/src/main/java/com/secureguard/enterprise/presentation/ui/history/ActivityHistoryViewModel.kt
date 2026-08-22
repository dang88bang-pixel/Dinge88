package com.secureguard.enterprise.presentation.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secureguard.enterprise.ct45p.CT45PLogExport
import com.secureguard.enterprise.ct45p.CT45PLogManager
import com.secureguard.enterprise.data.model.AuditLog
import com.secureguard.enterprise.services.AuditLogService
import com.secureguard.enterprise.util.ActivityStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** Filter für den Aktivitätsverlauf. */
enum class HistoryFilter { ALL, ACTION, AGENT, REGISTER, OTHER }

/**
 * Lädt den Audit-Log (Echtzeit), den CT45P-Log (on-device) und die
 * 24h-Statistik; exportiert CSV / CT45P-Log und erzeugt Share-Intents.
 */
@HiltViewModel
class ActivityHistoryViewModel @Inject constructor(
    private val auditLogService: AuditLogService,
    private val ct45pLogManager: CT45PLogManager,
    private val ct45pLogExport: CT45PLogExport,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter.ALL)
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    /** Kompletter Audit-Log (Echtzeit). */
    val entries: StateFlow<List<AuditLog>> = auditLogService.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Gefilterter Audit-Log. */
    val filteredEntries: StateFlow<List<AuditLog>> =
        combine(entries, _filter) { list, f ->
            when (f) {
                HistoryFilter.ALL -> list
                HistoryFilter.ACTION -> list.filter { it.action.startsWith("ACTION") }
                HistoryFilter.AGENT -> list.filter { it.action.startsWith("AGENT") }
                HistoryFilter.REGISTER -> list.filter { it.action.startsWith("REGISTER") }
                HistoryFilter.OTHER -> list.filter {
                    !it.action.startsWith("ACTION") &&
                        !it.action.startsWith("AGENT") &&
                        !it.action.startsWith("REGISTER")
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 24h-Statistik aus der CT45P-Log-Datei. */
    val stats: StateFlow<ActivityStatsCalculator.ActivityStats> = entries
        .map { ActivityStatsCalculator.calculate(ct45pLogManager) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            ActivityStatsCalculator.ActivityStats())

    /** Letzter erstellter Export (für Share-Intent). */
    private val _lastExport = MutableStateFlow<File?>(null)
    val lastExport: StateFlow<File?> = _lastExport.asStateFlow()

    /** Pfad der on-device CT45P-Log-Datei (in Dateimanagern einsehbar). */
    val ct45pLogFilePath: String
        get() = ct45pLogManager.logFile.absolutePath

    fun setFilter(newFilter: HistoryFilter) {
        _filter.value = newFilter
    }

    /** Audit-Log als CSV exportieren. */
    fun exportAuditCsv() {
        viewModelScope.launch {
            val dir = exportDir()
            val file = File(dir, "audit_export_${System.currentTimeMillis()}.csv")
            file.writeText(buildString {
                appendLine("Timestamp;User;Action;Details")
                entries.value.forEach { e ->
                    appendLine("${e.timestamp};${e.userId};${e.action};${e.details}")
                }
            })
            _lastExport.value = file
        }
    }

    /** CT45P-Aktivitätslog (mit Statistik) exportieren. */
    fun exportCt45pLog() {
        viewModelScope.launch {
            _lastExport.value = ct45pLogExport.exportLogs()
        }
    }

    /** Share-Intent für den letzten Export (via FileProvider). */
    fun shareLastExport(): Intent? {
        val file = _lastExport.value ?: return null
        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SecureGuard-Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun exportDir(): File {
        val dir = File(context.getExternalFilesDir(null), "SecureGuard/Export")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    companion object {
        internal fun formatTimestamp(millis: Long): String =
            String.format(Locale.GERMANY, "%tF %tT", millis, millis)
    }
}
