---
name: compose-design-system
description: Baut oder ändert Jetpack-Compose-Oberflächen in SecureGuard mit den Sg-Bausteinen. Enthält die projektspezifischen Compose-Fallstricke.
---

# Compose mit dem SecureGuard-Design-System

## Bevor du eine Zeile schreibst

Prüfe, ob es den Baustein schon gibt:

```bash
grep -n "^fun Sg" app/src/main/java/com/secureguard/enterprise/presentation/designsystem/SgComponents.kt
```

Vorhanden: `SgCard` · `SgSectionHeader` · `SgStatusDot` · `SgPill` ·
`SgSignalBars` · `SgMeter` · `SgProgressRing` · `SgSparkline` ·
`SgMetricTile` · `SgQuickTile` · `SgEmptyState` · `SgSkeleton` ·
`SgConfirmDialog`.

Fehlt einer: **erst in `SgComponents.kt` ergänzen**, dann benutzen. Niemals
eine Einmalvariante im Screen bauen.

## Aufbau eines Screens

```kotlin
@Composable
fun FeatureScreen(
    navController: NavController,
    viewModel: FeatureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    FeatureContent(
        state = uiState,
        onAction = viewModel::onAction,
        onBack = { navController.popBackStack() }
    )
}

@Composable
private fun FeatureContent(
    state: FeatureUiState,
    onAction: (FeatureAction) -> Unit,
    onBack: () -> Unit
) { /* zustandslos, ohne ViewModel-Bezug */ }
```

Warum die Trennung: `FeatureContent` ist ohne Hilt in Previews und
Screenshot-Tests verwendbar.

## Zustandsführung

- ViewModel exponiert **einen** `StateFlow<UiState>`, keine fünf einzelnen.
- Einmalige Ereignisse (Snackbar, Navigation) als nullbares Feld plus
  `consumeX()`, nicht als Dauerzustand.
- `collectAsState()` im Screen, nicht tiefer in der Hierarchie.

## Projektspezifische Fallstricke

**1. Zustand in `LazyListScope` wird nicht neu zusammengesetzt.**

```kotlin
// FALSCH – liest Zustand erst im item-Lambda
LazyColumn { items(catalog.filter { it.category == selected }) { … } }

// RICHTIG – im Composable-Scope ableiten
val visible = remember(selected, favorites) {
    catalog.filter { it.category == selected }
}
LazyColumn { items(visible) { … } }
```

**2. `PullToRefreshBox` und `menuAnchor()` sind hier verboten** — die APIs
wechseln zwischen Material3-Versionen. Refresh über einen `IconButton` in der
`TopAppBar`, Menüs über `DropdownMenu` in einer `Box`.

**3. `FilterChip`** braucht je nach Version `@OptIn(ExperimentalMaterial3Api::class)`.

**4. Keys in Listen:** `items(list, key = { it.id })`, sonst springt der
Scroll-Zustand bei Aktualisierungen.

**5. Keine Berechnung im Composable-Körper** ohne `remember` — der Körper
läuft bei jeder Zusammensetzung.

## Farben und Abstände

```kotlin
// FALSCH
Card(colors = CardDefaults.cardColors(Color(0xFF0E1B2C)))

// RICHTIG
SgCard(accent = statusColor(asset.status)) { … }
```

Nachweis: `grep -rn "Color(0x" presentation/ui | wc -l` muss `0` ergeben.

## Berechtigungen

Jede mutierende Aktion im ViewModel:

```kotlin
if (!roleManager.has(Permission.EXECUTE_ACTIONS)) {
    _message.value = "Keine Berechtigung für diese Aktion (Rolle ${roleManager.currentRole})"
    return
}
```

Die Oberfläche erklärt die Ablehnung — sie versteckt den Knopf nicht.

## Nachweis

```bash
./gradlew :app:assembleDebug
./gradlew :app:lint
```

Danach Skill `secureguard-ui-review` durchgehen.
