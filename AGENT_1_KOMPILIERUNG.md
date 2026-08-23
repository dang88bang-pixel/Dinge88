# 🤖 AGENT 1 – Kompilierungsfehler & Presentation-Layer
## Scope: Presentation-Dateien (Screens, ViewModels, Components)

> **Ziel:** Alle Kompilierungsfehler beheben, damit `./gradlew :app:assembleDebug` erfolgreich durchläuft.  
> **Keine Änderungen an:** Services, DAOs, Repository, Backend, Firmware  
> **Betroffene Dateien:** ausschließlich `presentation/` + `data/model/Telemetry.kt` (nur Lesezugriff)

---

## TASK 1.1 – DashboardScreen: navController-Parameter hinzufügen

**Datei:** `app/src/main/java/com/secureguard/enterprise/presentation/ui/dashboard/DashboardScreen.kt`

### Aktuelle Signatur (FALSCH):
```kotlin
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
```

### Zielsignatur:
```kotlin
@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = hiltViewModel()
) {
```

### Änderungen:
- [ ] `import androidx.navigation.NavController` hinzufügen
- [ ] `navController: NavController` als ersten Parameter setzen
- [ ] Prüfen ob `navController` im Body referenziert wird (z. B. Navigation zu Alerts/Assets)

### Validierung:
```bash
grep -n "DashboardScreen(navController" app/src/main/java/com/secureguard/enterprise/presentation/navigation/SecureGuardApp.kt
# Muss: DashboardScreen(navController = navController) liefern
```

---

## TASK 1.2 – DashboardScreen: `resolved` → `acknowledged`

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`

### Aktuelles Problem:
```kotlin
StatCard(label = "Alarme", value = alerts.count { !it.resolved }.toString(), icon = "⚠️")
```
`Alert` hat kein Feld `resolved` – das Feld heißt `acknowledged`.

### Fix:
- [ ] Alle Vorkommen von `.resolved` durch `.acknowledged` ersetzen

### Validierung:
```bash
grep -rn "\.resolved" app/src/main/java/com/secureguard/enterprise/presentation/
# Muss: 0 Treffer (in diesem Kontext)
```

---

## TASK 1.3 – DashboardScreen: StatCard-Aufrufe korrigieren

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`

### Aktuelles Problem:
Die `StatCard`-Signatur ist:
```kotlin
fun StatCard(modifier: Modifier, value: String, label: String, icon: ImageVector, color: Color)
```
Die Screen nutzt aber String-Icons und lässt `modifier`/`color` weg.

### Fix – Alle 4 StatCard-Aufrufe ersetzen:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color

// Im Composable:
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    StatCard(
        modifier = Modifier.weight(1f),
        value = "$batteryLevel%",
        label = "Batterie",
        icon = Icons.Default.BatteryFull,
        color = Color(0xFF2E7D32)
    )
    StatCard(
        modifier = Modifier.weight(1f),
        value = assets.size.toString(),
        label = "Assets",
        icon = Icons.Default.LocationOn,
        color = Color(0xFF1565C0)
    )
    StatCard(
        modifier = Modifier.weight(1f),
        value = detections.size.toString(),
        label = "Detektionen",
        icon = Icons.Default.Search,
        color = Color(0xFF6A1B9A)
    )
    StatCard(
        modifier = Modifier.weight(1f),
        value = alerts.count { !it.acknowledged }.toString(),
        label = "Alarme",
        icon = Icons.Default.Warning,
        color = Color(0xFFC62828)
    )
}
```

### Imports prüfen/ergänzen:
- [ ] `import androidx.compose.foundation.layout.Arrangement`
- [ ] `import androidx.compose.foundation.layout.Row`
- [ ] `import androidx.compose.material.icons.Icons`
- [ ] `import androidx.compose.material.icons.filled.*`
- [ ] `import androidx.compose.ui.graphics.Color`

---

## TASK 1.4 – DashboardScreen: uiState aus ViewModel nutzen

**Datei:** `presentation/ui/dashboard/DashboardScreen.kt`

### Aktuelles Problem:
Screen sammelt separate Flows statt das reichhaltige `uiState`:
```kotlin
val assets by viewModel.assets.collectAsState(initial = emptyList())
val detections by viewModel.detections.collectAsState(initial = emptyList())
val alerts by viewModel.alerts.collectAsState(initial = emptyList())
```

### Fix – DashboardViewModel.uiState nutzen:
```kotlin
val uiState by viewModel.uiState.collectAsState()
```

StatCards mit uiState-Werten füllen:
```kotlin
StatCard(value = uiState.totalAssets.toString(), ...)
StatCard(value = uiState.onlineAssets.toString(), ...)
StatCard(value = uiState.alertCount.toString(), ...)
```

### DashboardViewModel erweitern:
**Datei:** `presentation/ui/dashboard/DashboardViewModel.kt`

- [ ] `val detections` und `val alerts` als öffentliche Flows hinzufügen (falls die Screen sie noch braucht):
```kotlin
val detections = repository.getAllDetections()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

val alerts = repository.getAlerts()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

---

## TASK 1.5 – AssetDetailScreen: navController-Parameter + LaunchedEffect

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`

### Aktuelles Problem:
- Kein `navController`-Parameter
- `viewModel.getAsset(assetId)` existiert nicht (ViewModel hat `loadAsset()` + `assetState`)
- `viewModel.getLatestTelemetry(assetId)` existiert nicht (ViewModel hat `telemetry` StateFlow)

### Zielsignatur:
```kotlin
@Composable
fun AssetDetailScreen(
    navController: NavController,
    assetId: String,
    viewModel: AssetDetailViewModel = hiltViewModel()
) {
```

### Body-Rewrite:
```kotlin
LaunchedEffect(assetId) {
    viewModel.loadAsset(assetId)
}

val asset by viewModel.assetState.collectAsState()
val telemetry by viewModel.telemetry.collectAsState()
val detections by viewModel.detections.collectAsState()
val isSearching by viewModel.isSearching.collectAsState()
```

### Scaffold mit TopAppBar:
```kotlin
Scaffold(
    topBar = {
        TopAppBar(
            title = { Text(asset?.shortName ?: "Asset") },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                }
            }
        )
    }
) { padding ->
    // Content hier
}
```

### Imports:
- [ ] `import androidx.navigation.NavController`
- [ ] `import androidx.compose.runtime.LaunchedEffect`
- [ ] `import androidx.compose.material3.Scaffold`
- [ ] `import androidx.compose.material3.TopAppBar`
- [ ] `import androidx.compose.material.icons.Icons`
- [ ] `import androidx.compose.material.icons.filled.ArrowBack`

---

## TASK 1.6 – AssetDetailScreen: Telemetrie-Felder korrigieren

**Datei:** `presentation/ui/assets/AssetDetailScreen.kt`

### Mapping-Tabelle:

| FALSCH (Screen) | RICHTIG (Telemetry-Modell) | Anzeige |
|---|---|---|
| `data.battery` | `data.batteryPercent` | `"🔋 ${data.batteryPercent}%"` |
| `data.fuel` | `data.fuelPercent` | `"⛽ ${data.fuelPercent}%"` |
| `data.motor` (U/min) | `data.motorOk` (Boolean) | `"🏃 Motor: ${if (data.motorOk) "OK" else "⚠ FEHLER"}"` |
| `data.distance` | `data.kilometers` | `"📍 ${"%.1f".format(data.kilometers ?: 0.0)} km"` |
| (fehlt) | `data.tiresOk` | `"🛞 Reifen: ${if (data.tiresOk) "OK" else "⚠ Prüfung"}"` |
| (fehlt) | `data.operatingHours` | `"⏱ ${"%.1f".format(data.operatingHours ?: 0.0)}h"` |

### Vollständiger Telemetrie-Block:
```kotlin
telemetry?.let { data ->
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📊 Telemetrie", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("🔋 Batterie: ${data.batteryPercent ?: "–"}%")
            Text("⛽ Kraftstoff: ${data.fuelPercent ?: "–"}%")
            Text("🏃 Motor: ${if (data.motorOk) "✅ OK" else "❌ Fehler"}")
            Text("🛞 Reifen: ${if (data.tiresOk) "✅ OK" else "⚠️ Prüfung nötig"}")
            Text("⏱ Betriebsstunden: ${"%.1f".format(data.operatingHours ?: 0.0)}h")
            Text("📍 Kilometerstand: ${"%.1f".format(data.kilometers ?: 0.0)} km")
        }
    }
} ?: Text("⏳ Telemetrie wird geladen...")
```

---

## TASK 1.7 – SecureGuardApp.kt: AssetDetailScreen-Aufruf korrigieren

**Datei:** `presentation/navigation/SecureGuardApp.kt`

### Aktuelles Problem:
```kotlin
AssetDetailScreen(
    navController = navController,
    assetId = entry.arguments?.getString("assetId").orEmpty()
)
```
AssetDetailScreen hat aktuell `assetId` als ersten Parameter.

### Fix (nach Task 1.5):
```kotlin
AssetDetailScreen(
    navController = navController,
    assetId = entry.arguments?.getString("assetId").orEmpty()
)
```
→ Sollte nach Task 1.5 korrekt sein (Signatur: `navController, assetId, viewModel`)

---

## TASK 1.8 – DashboardViewModel: Echte Batterie-Abfrage

**Datei:** `presentation/ui/dashboard/DashboardViewModel.kt`

### Aktuelles Problem:
```kotlin
private val battery = MutableStateFlow(87)
```

### Fix:
```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SecureGuardRepository,
    private val agentService: AgentService,
    @ApplicationContext private val context: Context  // <-- hinzufügen
) : ViewModel() {

    private val battery = MutableStateFlow(readBatteryLevel())

    private fun readBatteryLevel(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }
```

### Imports:
- [ ] `import android.content.Context`
- [ ] `import android.content.Intent`
- [ ] `import android.content.IntentFilter`
- [ ] `import android.os.BatteryManager`
- [ ] `import dagger.hilt.android.qualifiers.ApplicationContext`

---

## PRÜFUNG & TEST

### Nach allen Tasks:
```bash
# 1. Kotlin-Syntax-Check (alle Presentation-Dateien)
grep -rn "import" app/src/main/java/com/secureguard/enterprise/presentation/ui/dashboard/DashboardScreen.kt | wc -l
# 2. Keine .resolved-Vorkommen mehr
grep -rn "\.resolved" app/src/main/java/com/secureguard/enterprise/presentation/ | grep -v "Binary"
# 3. StatCard-Aufrufe prüfen
grep -n "StatCard(" app/src/main/java/com/secureguard/enterprise/presentation/ui/dashboard/DashboardScreen.kt
# 4. AssetDetailScreen-Signatur prüfen
grep -n "fun AssetDetailScreen" app/src/main/java/com/secureguard/enterprise/presentation/ui/assets/AssetDetailScreen.kt
# 5. Keine nicht-existierenden Telemetrie-Felder
grep -n "data\.battery\b\|data\.fuel\b\|data\.motor\b\|data\.distance\b" app/src/main/java/com/secureguard/enterprise/presentation/
```

### Vollständiger Build-Test:
```bash
cd /home/user/Dinge88 && ./gradlew :app:assembleDebug 2>&1 | tail -50
```

---

## ABNAHMEKRITERIEN

- [ ] `./gradlew :app:assembleDebug` kompiliert ohne Fehler
- [ ] DashboardScreen zeigt 4 StatCards mit ImageVector-Icons und Farben
- [ ] DashboardScreen zeigt echte Batterie-Prozent (nicht hardcoded 87)
- [ ] AssetDetailScreen hat Scaffold mit TopAppBar + Zurück-Button
- [ ] AssetDetailScreen zeigt Telemetrie mit korrekten Feldnamen
- [ ] Navigation von SecureGuardApp zu allen Screens funktioniert
- [ ] Keine `Random`-Imports in Presentation-Dateien
- [ ] Keine String-Icons ("🔋") wo ImageVector erwartet wird
