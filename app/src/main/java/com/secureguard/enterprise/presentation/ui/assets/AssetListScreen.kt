package com.secureguard.enterprise.presentation.ui.assets

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.AssetCard
import com.secureguard.enterprise.presentation.designsystem.Sg
import com.secureguard.enterprise.presentation.designsystem.SgCard
import com.secureguard.enterprise.presentation.designsystem.SgEmptyState
import com.secureguard.enterprise.presentation.designsystem.SgPill
import com.secureguard.enterprise.presentation.designsystem.statusColor
import com.secureguard.enterprise.presentation.designsystem.statusLabel
import com.secureguard.enterprise.presentation.navigation.Routes
import com.secureguard.enterprise.presentation.ui.common.ActionType

/**
 * Asset-Übersicht mit Suche, Statusfiltern, Sortierung, Flottenkennzahlen
 * und Direktaktionen je Karte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    navController: NavController,
    viewModel: AssetListViewModel = hiltViewModel()
) {
    val assets by viewModel.filteredAssets.collectAsState()
    val searchQuery by viewModel.currentQuery.collectAsState()
    val selectedStatus by viewModel.currentStatus.collectAsState()
    val sort by viewModel.currentSort.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHost = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("Assets") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sortierung")
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            AssetSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        viewModel.setSort(option)
                                        sortMenuOpen = false
                                    },
                                    trailingIcon = {
                                        if (option == sort) {
                                            Icon(Icons.Default.Sort, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { navController.navigate(Routes.SCAN_QR) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "QR scannen")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Routes.ADD_ASSET) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Asset") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Sg.Space.lg)
        ) {
            Spacer(Modifier.height(Sg.Space.sm))

            FleetSummary(
                total = uiState.total,
                online = uiState.online,
                offline = uiState.offline,
                maintenance = uiState.maintenance,
                averageBattery = uiState.averageBattery,
                weakSignal = uiState.weakSignal
            )

            Spacer(Modifier.height(Sg.Space.md))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Suchen: Name, Kurzname, ID oder MAC") },
                singleLine = true,
                shape = RoundedCornerShape(Sg.Radius.md),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Filter zurücksetzen")
                        }
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
                FilterChip(
                    selected = selectedStatus == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text("Alle") }
                )
                listOf(
                    AssetStatus.ONLINE,
                    AssetStatus.OFFLINE,
                    AssetStatus.MAINTENANCE,
                    AssetStatus.SEARCHING
                ).forEach { status ->
                    FilterChip(
                        selected = selectedStatus == status,
                        onClick = {
                            viewModel.setStatusFilter(if (selectedStatus == status) null else status)
                        },
                        label = { Text(statusLabel(status)) }
                    )
                }
            }

            Spacer(Modifier.height(Sg.Space.sm))
            Text(
                "Sortiert nach ${sort.label} · ${assets.size} Treffer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Sg.Space.sm))

            if (assets.isEmpty()) {
                SgEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "Keine Assets gefunden",
                    message = "Passe die Filter an oder lege ein neues Asset an – nur " +
                        "gewhitelistete Werte werden überhaupt gesucht.",
                    actionLabel = "Asset hinzufügen",
                    onAction = { navController.navigate(Routes.ADD_ASSET) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Sg.Space.md),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(assets, key = { it.id }) { asset ->
                        AssetCard(
                            asset = asset,
                            onClick = { navController.navigate(Routes.assetDetail(asset.id)) },
                            onLocate = { viewModel.quickAction(asset, ActionType.POSITION) },
                            onAlarm = { viewModel.quickAction(asset, ActionType.ALARM) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FleetSummary(
    total: Int,
    online: Int,
    offline: Int,
    maintenance: Int,
    averageBattery: Int?,
    weakSignal: Int
) {
    SgCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Sg.Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$total Assets im Bestand",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append("Ø Akku ")
                        append(averageBattery?.let { "$it%" } ?: "–")
                        if (weakSignal > 0) append(" · $weakSignal mit schwachem Signal")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Sg.Space.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Sg.Space.sm)
        ) {
            SgPill(text = "$online online", color = statusColor(AssetStatus.ONLINE), live = online > 0)
            SgPill(text = "$offline offline", color = statusColor(AssetStatus.OFFLINE))
            SgPill(text = "$maintenance Wartung", color = statusColor(AssetStatus.MAINTENANCE))
        }
    }
}
