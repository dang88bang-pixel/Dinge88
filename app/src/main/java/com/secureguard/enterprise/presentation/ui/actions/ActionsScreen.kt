package com.secureguard.enterprise.presentation.ui.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.designsystem.Sg
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgConfirmDialog
import com.secureguard.enterprise.presentation.designsystem.SgEmptyState
import com.secureguard.enterprise.presentation.designsystem.SgMeter
import com.secureguard.enterprise.presentation.designsystem.SgPill
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader
import com.secureguard.enterprise.presentation.designsystem.SgSignalBars
import com.secureguard.enterprise.presentation.designsystem.batteryColor
import com.secureguard.enterprise.presentation.designsystem.relativeTime
import com.secureguard.enterprise.presentation.designsystem.statusColor
import com.secureguard.enterprise.presentation.designsystem.statusLabel
import com.secureguard.enterprise.presentation.navigation.Routes
import com.secureguard.enterprise.presentation.ui.common.ActionCatalog
import com.secureguard.enterprise.presentation.ui.common.ActionCategory
import com.secureguard.enterprise.presentation.ui.common.ActionRisk
import com.secureguard.enterprise.presentation.ui.common.ActionSpec

/**
 * Aktions-Leitstand.
 *
 * Kernidee: Der Anwender sieht *vor* dem Absenden, was passiert – Ziel(e),
 * Risiko, Zustellweg – und *nach* dem Absenden, was tatsächlich passiert ist
 * (Direktkanal, Warteschlange, blockiert). Sammelbefehle, Favoriten,
 * Bestätigung für kritische Aktionen und ein filterbares Protokoll gehören
 * fest dazu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    navController: NavController,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerAssets by viewModel.pickerAssets.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val primary by viewModel.primaryTarget.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val note by viewModel.note.collectAsState()
    val query by viewModel.pickerQuery.collectAsState()
    val onlyOnline by viewModel.onlyOnline.collectAsState()
    val log by viewModel.log.collectAsState()
    val logFilter by viewModel.logFilter.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    var pendingConfirm by remember { mutableStateOf<ActionSpec?>(null) }
    var category by remember { mutableStateOf<ActionCategory?>(null) }

    val selectedCategory = category
    val visibleActions = remember(selectedCategory, favorites) {
        if (selectedCategory == null) {
            ActionCatalog.all.sortedByDescending { it.type in favorites }
        } else {
            ActionCatalog.byCategory(selectedCategory)
        }
    }
    val notesEnabled = visibleActions.any { it.acceptsNote }

    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = current.message,
            actionLabel = current.actionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.repeatLast()
        viewModel.consumeFeedback()
    }

    pendingConfirm?.let { spec ->
        SgConfirmDialog(
            title = spec.confirmTitle ?: "${spec.title} bestätigen",
            message = buildString {
                append(spec.confirmMessage ?: spec.description)
                append("\n\nZiele: ")
                append(targets.joinToString(", ") { it.shortName }.ifBlank { "—" })
            },
            confirmLabel = spec.title,
            icon = spec.icon,
            onConfirm = {
                pendingConfirm = null
                viewModel.execute(spec)
            },
            onDismiss = { pendingConfirm = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Aktions-Leitstand") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.OPS_3D) }) {
                        Icon(Icons.Default.ThreeDRotation, contentDescription = "3D-Lagebild")
                    }
                    IconButton(onClick = { viewModel.repeatLast() }, enabled = lastAction != null) {
                        Icon(Icons.Default.Replay, contentDescription = "Letzte Aktion wiederholen")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Sg.Space.lg),
            verticalArrangement = Arrangement.spacedBy(Sg.Space.md)
        ) {
            item { StatusStrip(uiState, onFlush = { viewModel.flushQueue() }) }

            item {
                TargetPicker(
                    assets = pickerAssets,
                    selectedIds = selectedIds,
                    query = query,
                    onlyOnline = onlyOnline,
                    onQuery = viewModel::setPickerQuery,
                    onToggleOnline = viewModel::toggleOnlyOnline,
                    onToggle = viewModel::toggleTarget,
                    onSelectSingle = viewModel::selectSingle,
                    onSelectAll = viewModel::selectAllVisible,
                    onClear = viewModel::clearSelection
                )
            }

            primary?.let { asset ->
                item { TargetDetail(asset = asset, extraTargets = targets.size - 1) }
            }

            item {
                CategoryRow(
                    selected = category,
                    favorites = favorites.size,
                    onSelect = { category = it }
                )
            }

            items(visibleActions, key = { it.type.name }) { spec ->
                ActionRow(
                    spec = spec,
                    favorite = spec.type in favorites,
                    enabled = uiState.canExecute && targets.isNotEmpty() && !uiState.executing,
                    running = uiState.runningAction == spec.type,
                    blockedHint = blockedHint(spec, targets),
                    onFavorite = { viewModel.toggleFavorite(spec.type) },
                    onExecute = {
                        if (spec.risk == ActionRisk.CRITICAL) pendingConfirm = spec
                        else viewModel.execute(spec)
                    }
                )
            }

            if (notesEnabled) {
                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nachrichtentext (optional)") },
                        supportingText = { Text("${note.length}/120 Zeichen") },
                        singleLine = true,
                        shape = RoundedCornerShape(Sg.Radius.md)
                    )
                }
            }

            item {
                LogHeader(
                    filter = logFilter,
                    onFilter = viewModel::setLogFilter,
                    onClear = viewModel::clearLog
                )
            }

            if (log.isEmpty()) {
                item {
                    SgEmptyState(
                        icon = Icons.Default.History,
                        title = "Noch keine Befehle",
                        message = "Ausgeführte Aktionen erscheinen hier mit Zustellweg und Ergebnis."
                    )
                }
            } else {
                items(log, key = { it.id }) { entry -> LogRow(entry) }
            }

            item { Spacer(Modifier.height(Sg.Space.xxl)) }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Bausteine                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun StatusStrip(uiState: ActionsUiState, onFlush: () -> Unit) {
    SgCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (uiState.canExecute) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error,
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
        ) {
            SgPill(
                text = "Rolle ${uiState.roleName}",
                color = if (uiState.canExecute) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                icon = if (uiState.canExecute) Icons.Default.CheckCircle else Icons.Default.Clear
            )
            SgPill(
                text = "${uiState.deliveredCount} zugestellt",
                color = statusColor(AssetStatus.ONLINE)
            )
            if (uiState.failedCount > 0) {
                SgPill(
                    text = "${uiState.failedCount} Probleme",
                    color = statusColor(AssetStatus.OFFLINE)
                )
            }
        }
        if (uiState.pendingQueue > 0) {
            Spacer(Modifier.height(Sg.Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(Sg.Size.icon)
                )
                Spacer(Modifier.width(Sg.Space.sm))
                Text(
                    "${uiState.pendingQueue} Befehl(e) in der Offline-Warteschlange",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onFlush) { Text("Jetzt senden") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetPicker(
    assets: List<Asset>,
    selectedIds: Set<String>,
    query: String,
    onlyOnline: Boolean,
    onQuery: (String) -> Unit,
    onToggleOnline: () -> Unit,
    onToggle: (Asset) -> Unit,
    onSelectSingle: (Asset) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    SgCard(modifier = Modifier.fillMaxWidth()) {
        SgSectionHeader(
            title = "Ziele",
            subtitle = if (selectedIds.isEmpty()) "Kein Ziel gewählt"
            else "${selectedIds.size} von ${assets.size} ausgewählt",
            icon = Icons.Default.PlaylistAddCheck,
            trailing = {
                Row {
                    TextButton(onClick = onSelectAll) { Text("Alle") }
                    TextButton(onClick = onClear) { Text("Keins") }
                }
            }
        )
        Spacer(Modifier.height(Sg.Space.md))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Name, Kurzname oder MAC …") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Suche leeren")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(Sg.Radius.md)
        )
        Spacer(Modifier.height(Sg.Space.sm))
        FilterChip(
            selected = onlyOnline,
            onClick = onToggleOnline,
            label = { Text("Nur erreichbare Ziele") },
            leadingIcon = {
                if (onlyOnline) Icon(Icons.Default.CheckCircle, contentDescription = null, Modifier.size(16.dp))
                else null
            },
            colors = FilterChipDefaults.filterChipColors()
        )
        Spacer(Modifier.height(Sg.Space.sm))

        if (assets.isEmpty()) {
            Text(
                "Keine Assets für diese Filter.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
            ) {
                assets.forEach { asset ->
                    TargetChip(
                        asset = asset,
                        selected = asset.id in selectedIds,
                        onClick = { onSelectSingle(asset) },
                        onLongClick = { onToggle(asset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetChip(
    asset: Asset,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = statusColor(asset.status)
    SgCard(
        modifier = Modifier.width(150.dp),
        accent = color,
        selected = selected,
        onClick = onClick,
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SgPill(text = statusLabel(asset.status), color = color, live = selected)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLongClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (selected) Icons.Default.CheckCircle else Icons.Default.StarBorder,
                    contentDescription = if (selected) "Aus Auswahl entfernen" else "Zur Auswahl hinzufügen",
                    tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(Sg.Space.xs))
        Text(
            asset.shortName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            relativeTime(asset.lastSeen?.time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun TargetDetail(asset: Asset, extraTargets: Int) {
    val color = statusColor(asset.status)
    SgCard(modifier = Modifier.fillMaxWidth(), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    asset.mac,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SgSignalBars(rssi = asset.rssi)
            Spacer(Modifier.width(Sg.Space.sm))
            SgPill(text = statusLabel(asset.status), color = color, live = asset.status == AssetStatus.ONLINE)
        }

        Spacer(Modifier.height(Sg.Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Sg.Space.lg)) {
            InfoBlock("Signal", if (asset.rssi == 0) "—" else "${asset.rssi} dBm", Modifier.weight(1f))
            InfoBlock("Gesehen", relativeTime(asset.lastSeen?.time), Modifier.weight(1f))
            InfoBlock(
                "Position",
                if (asset.latitude != null && asset.longitude != null)
                    "%.4f / %.4f".format(asset.latitude, asset.longitude) else "unbekannt",
                Modifier.weight(1.4f)
            )
        }

        asset.batteryLevel?.let { level ->
            Spacer(Modifier.height(Sg.Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Akku $level%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp)
                )
                SgMeter(
                    progress = level / 100f,
                    color = batteryColor(level),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (extraTargets > 0) {
            Spacer(Modifier.height(Sg.Space.sm))
            SgPill(
                text = "+$extraTargets weitere Ziele im Sammelbefehl",
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Bolt
            )
        }
    }
}

@Composable
private fun InfoBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryRow(
    selected: ActionCategory?,
    favorites: Int,
    onSelect: (ActionCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(if (favorites > 0) "Alle · ★$favorites" else "Alle") }
        )
        ActionCatalog.categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.label) },
                leadingIcon = { Icon(category.icon, contentDescription = null, Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun ActionRow(
    spec: ActionSpec,
    favorite: Boolean,
    enabled: Boolean,
    running: Boolean,
    blockedHint: String?,
    onFavorite: () -> Unit,
    onExecute: () -> Unit
) {
    val accent = when (spec.risk) {
        ActionRisk.CRITICAL -> MaterialTheme.colorScheme.error
        ActionRisk.CAUTION -> MaterialTheme.colorScheme.tertiary
        ActionRisk.SAFE -> MaterialTheme.colorScheme.primary
    }
    val active = enabled && blockedHint == null

    SgCard(
        modifier = Modifier.fillMaxWidth(),
        accent = accent,
        onClick = if (active) onExecute else null,
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Sg.Radius.sm)),
                contentAlignment = Alignment.Center
            ) {
                if (running) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = accent, modifier = Modifier.size(20.dp))
                } else {
                    Icon(spec.icon, contentDescription = null, tint = accent)
                }
            }
            Spacer(Modifier.width(Sg.Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    spec.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    blockedHint ?: spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (blockedHint != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (favorite) "Favorit entfernen" else "Als Favorit merken",
                    tint = if (favorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (spec.risk != ActionRisk.SAFE) {
            Spacer(Modifier.height(Sg.Space.sm))
            SgPill(text = "Risiko: ${spec.risk.label}", color = accent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogHeader(
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SgSectionHeader(
            title = "Befehlsprotokoll",
            subtitle = "Zustellweg und Ergebnis jeder Aktion",
            icon = Icons.Default.History,
            trailing = {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Protokoll leeren")
                }
            }
        )
        Spacer(Modifier.height(Sg.Space.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
        ) {
            LogFilter.entries.forEach { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { onFilter(entry) },
                    label = { Text(entry.label) }
                )
            }
        }
    }
}

@Composable
private fun LogRow(entry: CommandLogEntry) {
    val color = when (entry.state) {
        CommandState.DELIVERED -> statusColor(AssetStatus.ONLINE)
        CommandState.QUEUED -> statusColor(AssetStatus.MAINTENANCE)
        CommandState.RUNNING -> MaterialTheme.colorScheme.primary
        CommandState.DENIED, CommandState.BLOCKED -> statusColor(AssetStatus.OFFLINE)
    }
    SgCard(
        modifier = Modifier.fillMaxWidth(),
        accent = color,
        contentPadding = PaddingValues(horizontal = Sg.Space.md, vertical = Sg.Space.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Sg.Space.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${entry.actionType.label} → ${entry.assetName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.detail.isNotBlank()) {
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            SgPill(text = entry.state.label, color = color)
        }
    }
}

/** Erklärt dem Anwender, warum eine Aktion gerade nicht möglich ist. */
private fun blockedHint(spec: ActionSpec, targets: List<Asset>): String? = when {
    targets.isEmpty() -> "Bitte zuerst mindestens ein Ziel auswählen"
    spec.requiresOnline && targets.none { it.status == AssetStatus.ONLINE } ->
        "Verlangt ein erreichbares Ziel – aktuell ist keines online"
    else -> null
}
