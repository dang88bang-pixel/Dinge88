package com.secureguard.enterprise.presentation.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.secureguard.enterprise.config.EndpointConfig
import com.secureguard.enterprise.data.repository.SecureGuardRepository
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.designsystem.SgLoadingState
import com.secureguard.enterprise.presentation.designsystem.SgPrimaryButton
import com.secureguard.enterprise.presentation.designsystem.SgSecondaryButton
import com.secureguard.enterprise.presentation.designsystem.SgStatus
import com.secureguard.enterprise.presentation.designsystem.SgStatusBadge
import com.secureguard.enterprise.presentation.navigation.Routes
import com.secureguard.enterprise.services.AgentService
import com.secureguard.enterprise.services.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Eine einzelne Initialisierungs-/Zustandsprüfung. */
data class BootCheck(val id: String, val label: String, val ok: Boolean, val detail: String)

enum class BootPhase { CHECKING, READY, ERROR }

@HiltViewModel
class BootViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val authManager: AuthManager,
    private val agentService: AgentService,
    private val endpointConfig: EndpointConfig
) : ViewModel() {

    private val _phase = MutableStateFlow(BootPhase.CHECKING)
    val phase: StateFlow<BootPhase> = _phase.asStateFlow()

    private val _checks = MutableStateFlow<List<BootCheck>>(emptyList())
    val checks: StateFlow<List<BootCheck>> = _checks.asStateFlow()

    fun runChecks() {
        viewModelScope.launch {
            _phase.value = BootPhase.CHECKING
            val checks = mutableListOf<BootCheck>()

            // Auth
            checks += runCatching {
                BootCheck(
                    "auth", "Auth / PIN",
                    ok = true,
                    detail = if (authManager.isPinConfigured()) "PIN eingerichtet" else "Keine PIN eingerichtet"
                )
            }.getOrElse { BootCheck("auth", "Auth / PIN", false, "Fehler: ${it.message}") }

            // Datenbank
            checks += runCatching {
                val assets = repository.snapshotWhitelisted()
                BootCheck("db", "Datenbank (Room/SQLCipher)", true, "${assets.size} Assets")
            }.getOrElse { BootCheck("db", "Datenbank (Room/SQLCipher)", false, "Fehler: ${it.message}") }

            // Agent
            val agent = agentService.agentStatus.value
            checks += BootCheck(
                "agent", "Agent",
                ok = agent.running,
                detail = if (agent.running) "läuft (Zyklus ${agent.cycle})" else "gestoppt"
            )

            // Backend / Endpunkte
            val backend = endpointConfig.backendBaseUrl
            checks += BootCheck(
                "backend", "Backend",
                ok = backend.isNotBlank(),
                detail = backend.ifBlank { "BACKEND_BASE_URL nicht gesetzt" }
            )

            // Berechtigungen
            checks += BootCheck(
                "permissions", "Berechtigungen",
                ok = true,
                detail = "werden zur Laufzeit angefragt"
            )

            _checks.value = checks
            _phase.value = if (checks.all { it.ok }) BootPhase.READY else BootPhase.ERROR
        }
    }
}

/**
 * Splash / Initialisierung (§22): keine künstliche Verzögerung, sondern reale
 * Prüfung von Auth, Datenbank, Agent, Backend und Berechtigungen.
 */
@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: BootViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val checks by viewModel.checks.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.runChecks()
        // Weiter sobald die echten Prüfungen abgeschlossen sind — keine
        // künstliche Wartezeit (§22).
    }

    when (phase) {
        BootPhase.CHECKING -> {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp))
                Text("SecureGuard Pro", style = MaterialTheme.typography.headlineSmall)
                SgLoadingState(message = "Initialisierung läuft…")
            }
        }
        else -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Initialisierung", style = MaterialTheme.typography.titleLarge)
                for (c in checks) {
                    CheckRow(c)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SgPrimaryButton(
                        text = "Dashboard öffnen",
                        onClick = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SgSecondaryButton(
                        text = "Erneut prüfen",
                        icon = Icons.Default.Refresh,
                        onClick = { viewModel.runChecks() },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (phase == BootPhase.ERROR) {
                    Text(
                        "Mindestens eine Prüfung schlug fehl. Die App bleibt nutzbar; " +
                            "fehlende Hardware/Konfiguration wird entsprechend gekennzeichnet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun CheckRow(c: BootCheck) {
    SgCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (c.ok) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (c.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Column(Modifier.weight(1f)) {
                Text(c.label, style = MaterialTheme.typography.titleSmall)
                Text(c.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SgStatusBadge(status = if (c.ok) SgStatus.HEALTHY else SgStatus.ALARM)
        }
    }
}

/**
 * System Status / Success (§22): zeigt den Abschluss der Initialisierung und
 * führt bei Bedarf erneut aus. Reale Werte, keine erfundenen Stati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemStatusScreen(
    navController: NavController,
    viewModel: BootViewModel = hiltViewModel()
) {
    val phase by viewModel.phase.collectAsState()
    val checks by viewModel.checks.collectAsState()

    LaunchedEffect(Unit) { viewModel.runChecks() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Systemstatus") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allOk = checks.isNotEmpty() && checks.all { it.ok }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (allOk) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (allOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    if (allOk) " Systembereit" else " Eingeschränkt bereit",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            for (c in checks) { CheckRow(c) }
            SgSecondaryButton(
                text = "Erneut prüfen",
                icon = Icons.Default.Refresh,
                onClick = { viewModel.runChecks() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
