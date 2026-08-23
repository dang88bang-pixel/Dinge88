package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import androidx.paging.LoadState
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.components.AssetCard
import com.secureguard.enterprise.presentation.navigation.Routes
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    navController: NavController,
    viewModel: AssetListViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.currentQuery.collectAsState()
    val selectedStatus by viewModel.currentStatus.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Echte DB-Paginierung: Seite für Seite aus Room (Paging 3).
    val pagingItems: LazyPagingItems<com.secureguard.enterprise.data.model.Asset> =
        viewModel.pagedAssets.collectAsLazyPagingItems()

    // QR-Scan-Kette schließen: Der Scanner (aus dieser Liste gestartet) legt
    // die gescannte MAC im savedStateHandle dieser Liste ab – hier wird sie
    // entnommen und an das Hinzufügen-Formular weitergereicht.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow<String?>("scanned_mac", null)
            ?.collect { scanned ->
                if (!scanned.isNullOrBlank()) {
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.remove<String>("scanned_mac")
                    navController.navigate(Routes.ADD_ASSET)
                    navController.currentBackStackEntry
                        ?.savedStateHandle?.set("scanned_mac", scanned)
                }
            }
    }

    val tabs = listOf("Alle", "Online", "Offline", "Wartung")
    val selectedTabIndex = when (selectedStatus) {
        AssetStatus.ONLINE -> 1
        AssetStatus.OFFLINE -> 2
        AssetStatus.MAINTENANCE -> 3
        else -> 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📦 Assets (${uiState.total})") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SCAN_QR) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                    }
                    IconButton(onClick = { navController.navigate(Routes.ADD_ASSET) }) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("🔍 Asset suchen...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Löschen")
                        }
                    }
                }
            )

            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.padding(vertical = 8.dp),
                edgePadding = 0.dp
            ) {
                tabs.forEachIndexed { index, label ->
                    val status = when (index) {
                        1 -> AssetStatus.ONLINE
                        2 -> AssetStatus.OFFLINE
                        3 -> AssetStatus.MAINTENANCE
                        else -> null
                    }
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { viewModel.setStatusFilter(status) },
                        text = { Text(label) }
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (pagingItems.loadState.refresh is LoadState.Loading &&
                    pagingItems.itemCount == 0
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (pagingItems.itemCount == 0) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Keine Assets gefunden",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Füge ein Asset hinzu oder ändere die Filter",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(pagingItems, key = { it.id }) { asset ->
                        AssetCard(
                            asset = asset,
                            onClick = { navController.navigate(Routes.assetDetail(asset.id)) }
                        )
                    }
                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

            Divider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("📊 ${uiState.total}", style = MaterialTheme.typography.bodySmall)
                Text("🟢 ${uiState.online}", style = MaterialTheme.typography.bodySmall)
                Text("🔴 ${uiState.offline}", style = MaterialTheme.typography.bodySmall)
                Text("🟡 ${uiState.maintenance}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
