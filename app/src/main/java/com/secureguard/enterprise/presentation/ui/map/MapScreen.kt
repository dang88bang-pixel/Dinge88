package com.secureguard.enterprise.presentation.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.R
import com.secureguard.enterprise.data.model.AssetStatus
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastUpdate by viewModel.lastUpdate.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗺️ Kartenansicht") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = { viewModel.zoomIn() }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Vergrößern")
                    }
                    IconButton(onClick = { viewModel.zoomOut() }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Verkleinern")
                    }
                    IconButton(onClick = { viewModel.centerOnAssets() }) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Zentrieren")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Karte
            AndroidView(
                factory = { factoryContext ->
                    MapView(factoryContext).apply {
                        setBuiltInZoomControls(false)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(GeoPoint(52.52, 13.40))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { mapView ->
                    mapView.overlays.clear()

                    assets.forEach { asset ->
                        if (asset.latitude != null && asset.longitude != null) {
                            val drawableRes = when (asset.status) {
                                AssetStatus.ONLINE -> R.drawable.marker_green
                                AssetStatus.OFFLINE -> R.drawable.marker_red
                                AssetStatus.MAINTENANCE -> R.drawable.marker_yellow
                                else -> R.drawable.marker_gray
                            }
                            val icon = ContextCompat.getDrawable(context, drawableRes)
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(asset.latitude!!, asset.longitude!!)
                                icon?.let { setIcon(it) }
                                title = asset.shortName
                                subDescription = "📶 ${asset.rssi} dBm | ⏱ ${asset.lastSeen}"
                                setOnMarkerClickListener { _, _ ->
                                    navController.navigate("asset_detail/${asset.id}")
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                }
            )

            // Loading-Indikator
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Legende unten
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Legende:", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(Color.Green)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Online", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(16.dp))
                        LegendDot(Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offline", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(16.dp))
                        LegendDot(Color(0xFFFFA000))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Wartung", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Footer
            Text(
                text = "⏱ Letzte Aktualisierung: $lastUpdate | " +
                    "📍 ${assets.count { it.latitude != null }} Assets sichtbar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape)
    )
}
