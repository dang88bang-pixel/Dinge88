# 🤖 AGENT 5 – Strukturelle Anbindungen + Integration + UI-Polish
## Scope: ApiNodeManager, RoleManager, NFC-Flow, Settings→Agent, Dashboard-UI, Agent-Progress

> **Ziel:** Nicht-genutzte Komponenten einbinden (ApiNodeManager, RoleManager, NFC), Settings an Agent weitergeben, UI-Polish.  
> **Keine Änderungen an:** Detection-Services (BleService, WifiService, etc.), TelemetryService, Backend, Firmware  
> **Betroffene Dateien:** `AgentService.kt` (nur Integration), `MainActivity.kt`, `SettingsViewModel.kt`, `AgentViewModel.kt`, `NodeStatusViewModel.kt`, `SettingsScreen.kt`, `SecureGuardApplication.kt`, `security/RoleManager.kt`

---

## TASK 5.1 – ApiNodeManager in AgentService einbinden

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/AgentService.kt`

### Aktuelles Problem:
`AgentService.buildChannelList()` ruft die API-Services direkt auf, ohne den `ApiNodeManager` (der Circuit-Breaker, Ratenlimits, Health-Monitoring und adaptive Prioritäten bietet).

### Fix – Constructor erweitern:
```kotlin
@Singleton
class AgentService @Inject constructor(
    // ... bestehende Dependencies ...
    private val apiNodeManager: ApiNodeManager  // <-- hinzufügen
) {
```

### Fix – buildChannelList() erweitern:
Nach den bestehenden lokalen Kanälen (BLE, WiFi, LoRa, etc.) einen API-Kanal hinzufügen:

```kotlin
// Nach den bestehenden Kanälen (CROWD, SATELLITE):
all[DetectionSource.API] = {
    val apiDetections = apiNodeManager.queryAllNodes(
        mac = asset.mac,
        latitude = asset.latitude,
        longitude = asset.longitude
    )
    apiDetections.firstOrNull()  // Beste API-Detection
}
```

### Änderungen:
- [ ] `ApiNodeManager` importieren: `import com.secureguard.enterprise.agent.ApiNodeManager`
- [ ] Constructor-Parameter hinzufügen
- [ ] `DetectionSource.API`-Kanal in `buildChannelList()` hinzufügen
- [ ] Bedingung: Nur wenn `!settings.offlineOnly || settings.externalSources`
- [ ] Bestehende direkte API-Aufrufe (falls vorhanden) durch NodeManager ersetzen

### Validierung:
```bash
grep -n "apiNodeManager" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 2 Treffer (Constructor + buildChannelList)
```

---

## TASK 5.2 – NFC-Detection in AgentService integrieren

**Datei:** `app/src/main/java/com/secureguard/enterprise/services/AgentService.kt`  
**Datei:** `app/src/main/java/com/secureguard/enterprise/MainActivity.kt`

### Aktuelles Problem:
`MainActivity.onNewIntent()` ruft `nfcService.processTag(intent)` auf, aber das Ergebnis wird verworfen. Der `NfcService.detections`-Flow wird nirgendwo gesammelt.

### Fix – AgentService: NfcService-Detection-Flow sammeln

Constructor erweitern:
```kotlin
private val nfcService: NfcService  // <-- hinzufügen
```

In `startRealtimeChannels()` hinzufügen:
```kotlin
private var nfcCollectorJob: Job? = null

private fun startRealtimeChannels() {
    // ... MQTT + WebSocket Collector (bestehend) ...

    nfcCollectorJob = scope.launch {
        nfcService.detections.collect { detection ->
            handleNfcDetection(detection)
        }
    }
}

private suspend fun handleNfcDetection(detection: Detection) {
    // Detection persistieren
    persist(detection)
    // Asset-Status aktualisieren wenn bekannt
    updateAssetIfKnown(detection)
    // Notification
    notificationService.sendAlertNotification(
        "NFC-Tag erkannt",
        "Asset ${detection.assetMac} per NFC identifiziert"
    )
    // AuditLog
    auditLogService.log(
        action = "NFC_TAG",
        details = "Tag gelesen: ${detection.assetMac}"
    )
}
```

In `stop()` hinzufügen:
```kotlin
nfcCollectorJob?.cancel()
nfcCollectorJob = null
```

### Fix – MainActivity: Detection auch direkt persistieren

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.action == android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED ||
        intent.action == android.nfc.NfcAdapter.ACTION_TECH_DISCOVERED ||
        intent.action == android.nfc.NfcAdapter.ACTION_TAG_DISCOVERED
    ) {
        nfcService.processTag(intent)
        // Detection wird jetzt über den Flow vom AgentService gesammelt und persistiert
    }
}
```

### Änderungen:
- [ ] `NfcService` in `AgentService`-Constructor injizieren
- [ ] `nfcCollectorJob` in `startRealtimeChannels()` starten
- [ ] `handleNfcDetection()` implementieren (persist + updateAsset + notify + audit)
- [ ] `nfcCollectorJob` in `stop()` canceln

---

## TASK 5.3 – SettingsViewModel → AgentService kommunizieren

**Datei:** `app/src/main/java/com/secureguard/enterprise/presentation/ui/settings/SettingsViewModel.kt`

### Aktuelles Problem:
Settings-Änderungen landen nur in SharedPreferences, der laufende Agent nutzt sie nicht.

### Fix:
```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agentService: AgentService  // <-- hinzufügen
) : ViewModel() {

    // ... bestehende save()-Funktion erweitern:

    private fun save(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { current ->
            val next = transform(current)
            prefs.edit()
                .putBoolean(KEY_NOTIFICATIONS, next.notificationsEnabled)
                .putBoolean(KEY_CROWD, next.externalCrowdAllowed)
                .putBoolean(KEY_OFFLINE, next.offlineOnly)
                .putBoolean(KEY_LEARNING, next.learningMode)
                .putBoolean(KEY_DARK, next.darkMode)
                .putBoolean(KEY_CONSENT, next.consentGiven)
                .apply()

            // Agent mit neuen Settings neu starten wenn er läuft
            if (agentService.agentStatus.value.running) {
                agentService.stop()
                agentService.start(
                    AgentSettings(
                        interval = agentService.agentStatus.value.settings.interval,
                        dynamicPriority = agentService.agentStatus.value.settings.dynamicPriority,
                        learningMode = next.learningMode,
                        offlineOnly = next.offlineOnly,
                        externalSources = next.externalCrowdAllowed
                    )
                )
            }
            next
        }
    }
```

### Änderungen:
- [ ] `AgentService` als Constructor-Dependency hinzufügen
- [ ] `AgentSettings` importieren
- [ ] In `save()`: Wenn Agent läuft → stoppen + mit neuen Settings neu starten
- [ ] `learningMode`, `offlineOnly`, `externalSources` aus UI-State übernehmen

---

## TASK 5.4 – AgentViewModel: Settings aus SharedPreferences lesen

**Datei:** `presentation/ui/agent/AgentViewModel.kt`

### Aktuelles Problem:
```kotlin
fun saveSettings() {
    val settings = AgentSettings(
        // ...
        offlineOnly = true,        // HARDCODED
        externalSources = false    // HARDCODED
    )
}
```

### Fix:
```kotlin
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agentService: AgentService,
    @ApplicationContext private val context: Context  // <-- hinzufügen
) : ViewModel() {

    private val prefs = context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    fun saveSettings() {
        val state = config.value
        val settings = AgentSettings(
            interval = state.interval.coerceAtLeast(5),
            dynamicPriority = state.dynamicPriority,
            learningMode = state.learningMode,
            offlineOnly = prefs.getBoolean("offline_only", true),
            externalSources = prefs.getBoolean("external_crowd", false)
        )
        agentService.start(settings)
    }
}
```

### Änderungen:
- [ ] `@ApplicationContext context: Context` injizieren
- [ ] SharedPreferences lesen (`offline_only`, `external_crowd`)
- [ ] Hardcoded `offlineOnly = true` / `externalSources = false` durch SharedPreferences-Werte ersetzen

---

## TASK 5.5 – AgentViewModel: Echten Progress berechnen

**Datei:** `presentation/ui/agent/AgentViewModel.kt`

### Aktuelles Problem:
```kotlin
progress = if (status.running) 85f else 0f  // Fake
```

### Fix:
```kotlin
val uiState: StateFlow<AgentUiState> = combine(
    agentService.agentStatus,
    config
) { status, cfg ->
    val progress = if (status.running && status.startedAt != null) {
        val intervalMs = status.settings.interval.coerceAtLeast(5) * 1000L
        val elapsed = System.currentTimeMillis() - (status.lastRunAt ?: status.startedAt!!)
        (elapsed.toFloat() / intervalMs * 100f).coerceIn(0f, 100f)
    } else 0f

    cfg.copy(
        agentRunning = status.running,
        runtime = formatUptime(status.uptimeMillis),
        progress = progress
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentUiState())
```

### Änderungen:
- [ ] Progress aus `elapsed / interval` berechnen (Fortschritt bis nächster Zyklus)
- [ ] `coerceIn(0f, 100f)` für saubere Prozentanzeige
- [ ] Hardcoded `85f` entfernen

---

## TASK 5.6 – RoleManager in Actions einbinden

**Datei:** `presentation/ui/actions/ActionsViewModel.kt`  
**Datei:** `presentation/ui/assets/AssetDetailViewModel.kt`

### Fix – Permission-Check vor Aktion:

In `ActionsViewModel.executeAction()`:
```kotlin
fun executeAction(actionType: ActionType) {
    viewModelScope.launch {
        val asset = selectedAsset.value ?: return@launch

        // RBAC-Check
        val user = User(id = "local", name = "Admin", role = Role.ADMIN)
        if (!RoleManager.hasPermission(user, Permission.EXECUTE_ACTIONS)) {
            _commandLog.value = _commandLog.value + "⛔ Keine Berechtigung"
            return@launch
        }

        _isExecuting.value = true
        // ... Rest der Funktion unverändert ...
    }
}
```

### Änderungen:
- [ ] `RoleManager` und `User`/`Role`/`Permission` importieren
- [ ] Default-User erstellen (ADMIN für Einzelgeräte)
- [ ] Permission-Check vor jeder Aktion
- [ ] Bei fehlender Permission: Log-Eintrag + kein Senden
- [ ] Gleiches Muster in `AssetDetailViewModel.executeAction()`

### Hinweis:
Aktuell läuft alles als ADMIN (Einzelgeräte-Betrieb). Die RBAC-Infrastruktur ist vorbereitet für Multi-User.

---

## TASK 5.7 – NodeStatusViewModel: Dynamic Test-MAC

**Datei:** `presentation/ui/nodes/NodeStatusViewModel.kt`

### Aktuelles Problem:
```kotlin
fun runFullQuery() {
    apiNodeManager.queryAllNodes(mac = "AA:BB:CC:DD:EE:01", ...)  // Hardcoded
}
```

### Fix:
```kotlin
@HiltViewModel
class NodeStatusViewModel @Inject constructor(
    private val apiNodeManager: ApiNodeManager,
    private val repository: SecureGuardRepository  // <-- hinzufügen
) : ViewModel() {

    fun runFullQuery() {
        viewModelScope.launch {
            // Erstes Asset aus der DB als Test-MAC verwenden
            val assets = repository.getAllAssets().first()
            val testMac = assets.firstOrNull()?.mac ?: return@launch
            val testAsset = assets.first()
            apiNodeManager.queryAllNodes(
                mac = testMac,
                latitude = testAsset.latitude,
                longitude = testAsset.longitude
            )
            refresh()
        }
    }
}
```

### Änderungen:
- [ ] `SecureGuardRepository` injizieren
- [ ] `import kotlinx.coroutines.flow.first`
- [ ] Erstes Asset aus DB lesen statt hardcoded MAC
- [ ] Wenn keine Assets: Early-Return (keine Query)

---

## TASK 5.8 – SettingsScreen: Profil-Daten dynamisch

**Datei:** `presentation/ui/settings/SettingsScreen.kt`

### Aktuelles Problem:
```kotlin
Text("Benutzer: Wache Mitte")              // Hardcoded
Text("Organisation: SecureGuard Enterprise")  // Hardcoded
```

### Fix:
```kotlin
Text("Benutzer: ${state.userName}", style = MaterialTheme.typography.bodyMedium)
Text("Organisation: ${state.organization}", style = MaterialTheme.typography.bodySmall, ...)
```

In `SettingsViewModel`:
```kotlin
data class SettingsUiState(
    // ... bestehende Felder ...
    val userName: String = "Admin",
    val organization: String = "SecureGuard"
)
```

SharedPreferences:
```kotlin
private fun load() = SettingsUiState(
    // ...
    userName = prefs.getString(KEY_USERNAME, "Admin") ?: "Admin",
    organization = prefs.getString(KEY_ORG, "SecureGuard") ?: "SecureGuard"
)
```

### Änderungen:
- [ ] `SettingsUiState` um `userName` und `organization` erweitern
- [ ] SharedPreferences-Keys: `KEY_USERNAME`, `KEY_ORG`
- [ ] Screen nutzt `state.userName` / `state.organization`

---

## TASK 5.9 – SecureGuardApplication: Demo-Seed optional

**Datei:** `app/src/main/java/com/secureguard/enterprise/SecureGuardApplication.kt`

### Aktuelles Problem:
`seedDemoDataIfEmpty()` sät immer 5 Fake-Assets beim ersten Start.

### Fix:
```kotlin
override fun onCreate() {
    super.onCreate()
    // ...
    if (BuildConfig.DEBUG) {
        seedDemoDataIfEmpty()  // Nur im Debug-Build
    }
    scheduleAgentWorker()
}
```

### Änderungen:
- [ ] `seedDemoDataIfEmpty()` nur bei `BuildConfig.DEBUG` aufrufen
- [ ] Release-Build startet mit leerer Datenbank (User muss Assets manuell anlegen)

---

## TASK 5.10 – OfflineQueue Auto-Flush im Agent-Cycle

**Datei:** `services/AgentService.kt`

### Fix – Am Ende von runCycle():
```kotlin
suspend fun runCycle(settings: AgentSettings = _agentStatus.value.settings): AgentCycleResult {
    // ... bestehender Code ...

    // Offline-Queue flushen wenn MQTT verbunden
    if (mqttService.isConnected) {
        val flushed = flushOfflineQueue()
        if (flushed > 0) {
            auditLogService.log(
                action = "QUEUE_FLUSH",
                details = "$flushed Aktionen aus Offline-Queue zugestellt"
            )
        }
    }

    return AgentCycleResult(assetsChecked = snapshot.size, detections = hits, channelHits = channelHits)
}
```

### Änderungen:
- [ ] `flushOfflineQueue()` am Ende jedes Cycles aufrufen wenn MQTT verbunden
- [ ] AuditLog-Eintrag bei erfolgreichem Flush

---

## PRÜFUNG & TEST

```bash
# 1. AgentService hat ApiNodeManager
grep -n "apiNodeManager" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 2 Treffer

# 2. AgentService hat NfcService
grep -n "nfcService\|nfcCollectorJob" app/src/main/java/com/secureguard/enterprise/services/AgentService.kt
# Erwartet: mindestens 3 Treffer

# 3. SettingsViewModel hat AgentService
grep -n "agentService" app/src/main/java/com/secureguard/enterprise/presentation/ui/settings/SettingsViewModel.kt
# Erwartet: mindestens 2 Treffer

# 4. AgentViewModel liest SharedPreferences
grep -n "prefs\|getBoolean" app/src/main/java/com/secureguard/enterprise/presentation/ui/agent/AgentViewModel.kt
# Erwartet: mindestens 3 Treffer

# 5. Kein hardcoded 85f mehr
grep -n "85f" app/src/main/java/com/secureguard/enterprise/presentation/ui/agent/AgentViewModel.kt
# Erwartet: 0 Treffer

# 6. RoleManager wird in Actions genutzt
grep -n "RoleManager\|hasPermission" app/src/main/java/com/secureguard/enterprise/presentation/ui/actions/ActionsViewModel.kt
# Erwartet: mindestens 2 Treffer

# 7. NodeStatusViewModel nutzt Repository
grep -n "repository" app/src/main/java/com/secureguard/enterprise/presentation/ui/nodes/NodeStatusViewModel.kt
# Erwartet: mindestens 2 Treffer

# 8. Kein hardcoded "AA:BB:CC:DD:EE:01" in NodeStatusViewModel
grep -n "AA:BB:CC:DD:EE:01" app/src/main/java/com/secureguard/enterprise/presentation/ui/nodes/NodeStatusViewModel.kt
# Erwartet: 0 Treffer

# 9. Demo-Seed nur bei DEBUG
grep -B1 "seedDemoDataIfEmpty" app/src/main/java/com/secureguard/enterprise/SecureGuardApplication.kt | grep "DEBUG"
# Erwartet: 1 Treffer
```

---

## ABNAHMEKRITERIEN

- [ ] `AgentService` nutzt `ApiNodeManager` für API-Kanäle (Circuit-Breaker + Ratenlimits)
- [ ] `AgentService` sammelt `NfcService.detections` Flow und persistiert NFC-Tags
- [ ] `SettingsViewModel` startet Agent mit neuen Settings neu bei Änderung
- [ ] `AgentViewModel` liest `offlineOnly`/`externalSources` aus SharedPreferences
- [ ] `AgentViewModel` berechnet Progress dynamisch aus `elapsed/interval`
- [ ] `ActionsViewModel` prüft RBAC-Permission vor jeder Aktion
- [ ] `NodeStatusViewModel` nutzt erstes Asset aus DB statt hardcoded MAC
- [ ] `SettingsScreen` zeigt Profil-Daten aus SharedPreferences
- [ ] Demo-Seed nur bei `BuildConfig.DEBUG`
- [ ] OfflineQueue wird bei jedem Agent-Cycle automatisch geflusht (wenn MQTT verbunden)
