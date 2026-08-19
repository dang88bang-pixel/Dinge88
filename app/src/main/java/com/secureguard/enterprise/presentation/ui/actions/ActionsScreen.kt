package com.secureguard.enterprise.presentation.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.enterprise.presentation.components.ActionButton

private data class ActionItem(
    val label: String,
    val color: Color,
    val key: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val selectedAssetId by viewModel.selectedAssetId.collectAsState()
    val lastAction by viewModel.lastAction.collectAsState()

    var expanded by remember { mutableStateOf(false) }
    val selectedAsset = assets.firstOrNull { it.id == selectedAssetId }

    val actions = listOf(
        ActionItem("🚨 Alarm", Color(0xFFD32F2F), "alarm"),
        ActionItem("🔧 Motor", Color(0xFF1976D2), "motor"),
        ActionItem("🔋 Batterie", Color(0xFF388E3C), "battery"),
        ActionItem("💬 Nachricht", Color(0xFFFBC02D), "message"),
        ActionItem("📍 Position anfordern", Color(0xFF8E24AA), "position")
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fernsteuerung") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Asset auswählen",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedAsset?.shortName ?: "Kein Asset gewählt",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Asset") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        assets.forEach { asset ->
                            DropdownMenuItem(
                                text = { Text(asset.shortName) },
                                onClick = {
                                    viewModel.selectAsset(asset.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Aktionen für ${selectedAsset?.shortName ?: "–"}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(actions.size) { index ->
                val action = actions[index]
                ActionButton(
                    label = action.label,
                    onClick = { viewModel.triggerAction(action.key) },
                    containerColor = action.color
                )
            }
            item {
                if (lastAction != null) {
                    Text(
                        "Letzte Aktion: $lastAction",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
