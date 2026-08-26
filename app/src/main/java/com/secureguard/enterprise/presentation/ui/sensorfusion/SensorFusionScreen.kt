package com.secureguard.enterprise.presentation.ui.sensorfusion

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secureguard.enterprise.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorFusionScreen(
    navController: NavController,
    viewModel: SensorFusionViewModel = hiltViewModel()
) {
    val fusionState by viewModel.fusionState.collectAsState()
    val assets by viewModel.assets.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sensor_fusion)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Fusion Status Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (fusionState.isActive) Color(0xFF00E676) else Color(0xFFFF1744))
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (fusionState.isActive) "Live-Tracking" else "Inaktiv",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (fusionState.isActive) Color(0xFF00E676) else Color(0xFFFF1744)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "OBJ-${assets.size}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.fusion_status), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricValue("${fusionState.confidence}%", stringResource(R.string.sf_confidence))
                        MetricValue("± ${fusionState.deviation}m", stringResource(R.string.sf_deviation))
                        MetricValue("${fusionState.channels}/9", stringResource(R.string.sf_channels))
                    }
                }
            }

            // GPS
            SensorCard(Icons.Default.GpsFixed, "GPS", fusionState.gpsStatus, listOf(
                "Lat / Long" to "${fusionState.latitude}° N, ${fusionState.longitude}° E",
                stringResource(R.string.sf_satellites) to "${fusionState.satellites} / 14",
                stringResource(R.string.sf_signal_strength) to fusionState.gpsSignal
            ))

            // Magnetometer
            SensorCard(Icons.Default.Explore, "Magnetometer", if (fusionState.isActive) stringResource(R.string.sf_calibrated) else stringResource(R.string.fusion_status_standby), listOf(
                stringResource(R.string.sf_axis_x) to "${fusionState.magX} μT",
                stringResource(R.string.sf_axis_y) to "${fusionState.magY} μT",
                stringResource(R.string.sf_axis_z) to "${fusionState.magZ} μT"
            ))

            // Kompass
            SensorCard(Icons.Default.Navigation, stringResource(R.string.sf_compass), "${fusionState.heading}° ${fusionState.headingDir}", emptyList())

            // Netzwerk RSSI
            SensorCard(Icons.Default.WifiTethering, stringResource(R.string.sf_network_rssi), stringResource(R.string.sf_nodes_count, fusionState.networkNodes), fusionState.rssiNodes)

            // Assets mit Position
            if (assets.isNotEmpty()) {
                Text(stringResource(R.string.tracked_assets), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                assets.take(5).forEach { asset ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(asset.shortName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${asset.latitude ?: 0.0}, ${asset.longitude ?: 0.0}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "📶 ${asset.rssi} dBm",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorCard(icon: ImageVector, title: String, status: String, data: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(status, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (data.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                data.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricValue(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
