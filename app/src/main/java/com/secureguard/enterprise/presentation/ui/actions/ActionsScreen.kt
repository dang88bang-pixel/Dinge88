package com.secureguard.enterprise.presentation.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.PendingAction
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgConfirmDialog
import com.secureguard.enterprise.presentation.designsystem.SgEmptyState
import com.secureguard.enterprise.presentation.designsystem.SgIconButton
import com.secureguard.enterprise.presentation.designsystem.SgPrimaryButton
import com.secureguard.enterprise.presentation.designsystem.SgSectionHeader
import com.secureguard.enterprise.presentation.designsystem.SgStatus
import com.secureguard.enterprise.presentation.designsystem.SgStatusBadge
import com.secureguard.enterprise.presentation.ui.common.ActionCategory
import com.secureguard.enterprise.presentation.ui.common.ActionDefinition
import com.secureguard.enterprise.presentation.ui.common.ActionRisk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    navController: NavController,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val selected by viewModel.selectedIds.collectAsState()
    val pending by viewModel.pendingActions.collectAsState()
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val executing by viewModel.executing.collectAsState()
    val message by viewModel.message.collectAsState()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<ActionCategory?>(null) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<ActionDefinition?>(null) }
    val confirmShow = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("⚡ Actions Center") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    SgIconButton(
                        icon = if (favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Nur Favoriten",
                        onClick = { favoritesOnly = !favoritesOnly }
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Suche + Filter
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; viewModel.setSearch(it) },
                    label = { Text("Aktion suchen") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionCategory.entries.forEach { c ->
                        val active = category == c
                        TextButton(onClick = { category = if (active) null else c; viewModel.setCategory(category) }) {
                            Text(
                                c.label,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Asset-Auswahl (Multi-Select)
            item {
                SgSectionHeader(
                    title = "Ziel-Assets (${selected.size} ausgewählt)",
                    actionLabel = if (selected.isEmpty()) null else "Alle abwählen",
                    onAction = if (selected.isEmpty()) null else { viewModel.clearSelection() }
                )
            }
            if (assets.isEmpty()) {
                item {
                    SgEmptyState(
                        icon = Icons.Default.CloudOff,
                        title = "Keine Assets",
                        message = "Keine geschützten Assets vorhanden – lege zuerst ein Asset an."
                    )
                }
            } else {
                items(assets, key = { it.id }) { asset ->
                    val isSel = asset.id in selected
                    SgCard(onClick = { viewModel.toggleSelected(asset.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isSel, onCheckedChange = { viewModel.toggleSelected(asset.id) })
                            Column(Modifier.weight(1f)) {
                                Text(asset.shortName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${asset.mac} · ✦ ${asset.rssi} dBm",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Aktionskatalog
            item {
                SgSectionHeader(title = "Katalog")
            }
            val catalog = viewModel.filteredCatalog(favoritesOnly)
            if (catalog.isEmpty()) {
                item {
                    SgEmptyState(
                        icon = Icons.Default.Search,
                        title = "Keine Aktionen",
                        message = "Keine Aktion passt zum Filter."
                    )
                }
            } else {
                items(catalog, key = { it.key }) { def ->
                    ActionCatalogRow(
                        def = def,
                        isFavorite = def.key in favorites,
                        onFavorite = { viewModel.toggleFavorite(def.key) },
                        onClick = {
                            if (def.isCritical) {
                                confirmAction = def
                                confirmShow.value = true
                            } else {
                                viewModel.execute(def)
                            }
                        }
                    )
                }
            }

            // Offline-Queue
            item {
                SgSectionHeader(
                    title = "Offline-Queue (${pending.size})",
                    actionLabel = if (pending.isEmpty()) null else "Retry",
                    onAction = if (pending.isEmpty()) null else { viewModel.retryQueue() }
                )
            }
            if (pending.isEmpty()) {
                item {
                    Text(
                        "Queue leer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(pending, key = { it.id }) { action ->
                    QueueRow(action = action, onRemove = { viewModel.removeQueueEntry(action.id) })
                }
            }

            // Verlauf
            item { SgSectionHeader(title = "Verlauf") }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Noch keine Aktionen ausgeführt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(history.takeLast(15)) { h ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (h.success) Icons.Default.CheckCircle else Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (h.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            "  ${h.at} › ${h.label}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    // Status-Nachricht (In-App, kein Fake-Toast)
    message?.let {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            SgCard(modifier = Modifier.padding(16.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(it, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") }
                }
            }
        }
    }

    // Bestätigung kritischer Aktionen (§7)
    confirmAction?.let { def ->
        SgConfirmDialog(
            show = confirmShow,
            title = "${def.label} bestätigen",
            message = "Diese Aktion hat Risiko ${def.risk.label}. " +
                "Sie wird auf ${selected.size} Asset(s) angewendet und an die bestehenden Kanäle übergeben.",
            confirmLabel = "Ausführen",
            onConfirm = { viewModel.execute(def) }
        )
    }
}

@Composable
private fun ActionCatalogRow(
    def: ActionDefinition,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val riskStatus = when (def.risk) {
        ActionRisk.LOW -> SgStatus.HEALTHY
        ActionRisk.MEDIUM -> SgStatus.WARNING
        ActionRisk.HIGH -> SgStatus.WARNING
        ActionRisk.CRITICAL -> SgStatus.ALARM
    }
    SgCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(def.icon, contentDescription = null, tint = riskStatus.color)
                    Text(
                        "  ${def.label}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SgStatusBadge(status = riskStatus)
                    SgIconButton(
                        icon = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "Aus Favoriten entfernen" else "Zu Favoriten",
                        onClick = onFavorite,
                        tint = if (isFavorite) Color(0xFFFFC400) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                def.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${def.category.label} · Risiko ${def.risk.label} · ${if (def.isScene) "3D-Szene" else if (def.kind == com.secureguard.enterprise.presentation.ui.common.ActionKind.SERVICE) "Service" else "Geräteaktion"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QueueRow(action: PendingAction, onRemove: () -> Unit) {
    SgCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${action.actionType} → ${action.assetMac}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Versuche: ${action.attempts}" + (action.lastError?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SgIconButton(
                icon = Icons.Default.Delete,
                contentDescription = "Queue-Eintrag entfernen",
                onClick = onRemove
            )
        }
    }
}
