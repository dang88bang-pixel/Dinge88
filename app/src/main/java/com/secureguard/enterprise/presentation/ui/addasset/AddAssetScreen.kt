package com.secureguard.enterprise.presentation.ui.addasset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAssetScreen(
    navController: NavController,
    viewModel: AddAssetViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var shortName by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var vin by remember { mutableStateOf("") }

    // Übernimmt das zuletzt gescannte QR-Ergebnis automatisch als MAC/ID,
    // sobald der Nutzer vom Scan-Screen zurückkehrt.
    val scannedValue by viewModel.scannedValue.collectAsState()
    LaunchedEffect(scannedValue) {
        scannedValue?.let { scanned ->
            if (mac.isBlank()) mac = scanned
            if (name.isBlank()) name = scanned
            viewModel.consumeScannedValue()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("➕ Asset hinzufügen") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Bitte die Asset-Daten eingeben. MAC/ID kann per QR-Scan erfasst werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = shortName,
                onValueChange = { shortName = it },
                label = { Text("Kurzname") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                label = { Text("MAC-Adresse (BLE)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = vin,
                onValueChange = { vin = it },
                label = { Text("VIN (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate("scan") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null
                    )
                    Text("📷 QR-Scan")
                }
                Button(
                    onClick = {
                        viewModel.saveAsset(name, shortName, mac, vin) {
                            navController.navigateUp()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("💾 Speichern")
                }
            }
        }
    }
}
