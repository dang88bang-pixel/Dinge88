package com.secureguard.enterprise.presentation.ui.assets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.components.AssetCard

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    navController: NavController,
    viewModel: AssetViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Assets") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.addSampleAsset() }) {
                Icon(Icons.Filled.Add, contentDescription = "Asset hinzufügen")
            }
        }
    ) { paddingValues ->
        if (assets.isEmpty()) {
            Text(
                text = "Keine Assets vorhanden.\nTippe auf +, um ein Demo-Asset hinzuzufügen.",
                modifier = Modifier.padding(paddingValues).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(assets, key = { it.id }) { asset ->
                    AssetCard(
                        asset = asset,
                        onClick = { navController.navigate("asset_detail/${asset.id}") },
                        onSearch = { viewModel.setStatus(asset.id, com.secureguard.enterprise.data.model.AssetStatus.SEARCHING) }
                    )
                }
            }
        }
    }
}
