package com.secureguard.enterprise.presentation.ui.assets

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.secureguard.enterprise.services.OpticalService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ScanQrViewModel @Inject constructor(
    private val opticalService: OpticalService
) : ViewModel() {
    fun onCodeScanned(code: String) {
        opticalService.setScannedCode(code)
    }
}

/**
 * QR / barcode scanner screen. Uses ZXing embedded so no external scanner app
 * is required. If the camera permission is denied the user can fall back to
 * typing the MAC address manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrScreen(
    navController: NavController,
    viewModel: ScanQrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scanned by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📷 QR-Scan") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasPermission) {
                val barcodeView = remember {
                    DecoratedBarcodeView(context).apply {
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                result?.text?.let { value ->
                                    if (scanned == null) scanned = value
                                }
                            }
                        })
                        resume()
                    }
                }
                DisposableEffect(Unit) {
                    barcodeView.resume()
                    onDispose { barcodeView.pause() }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { barcodeView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            } else {
                Text(
                    "Kamera-Berechtigung erforderlich",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Berechtigung erteilen")
                }
            }

            scanned?.let { value ->
                Text("Erkannt: $value", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        viewModel.onCodeScanned(value)
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.set("scanned_mac", value)
                        navController.popBackStack()
                    }
                ) { Text("Übernehmen") }
            }
            Text("oder manuell eingeben:", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it.uppercase() },
                label = { Text("MAC-Adresse") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    viewModel.onCodeScanned(manual)
                    navController.previousBackStackEntry
                        ?.savedStateHandle?.set("scanned_mac", manual)
                    navController.popBackStack()
                },
                enabled = manual.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Weiter") }
        }
    }
}
