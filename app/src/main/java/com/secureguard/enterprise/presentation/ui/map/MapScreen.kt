package com.secureguard.enterprise.presentation.ui.map

import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.AssetStatus
import com.secureguard.enterprise.presentation.navigation.Routes
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lastUpdate by viewModel.lastUpdate.collectAsState()
    val zoom by viewModel.zoom.collectAsState()

    Configuration.getInstance().userAgentValue = "SecureGuardEnterprise"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🗺️ Kartenansicht") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = { viewModel.zoomIn() }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Vergrößern")
                    }
                    IconButton(onClick = { viewModel.zoomOut() }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Verkleinern")
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
            val context = LocalContext.current
            val mapView = remember {
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(false)
                    setMultiTouchControls(true)
                    // Neutraler Initial-Anblick (Mitteldeutschland, weit
                    // herausgezoomt) – sofort danach Zentrierung auf die
                    // echten Asset-Positionen (siehe update-Block).
                    controller.setZoom(6.0)
                    controller.setCenter(GeoPoint(48.5, 10.0))
                }
            }

            DisposableEffect(Unit) {
                mapView.onResume()
                onDispose { mapView.onPause() }
            }

            AndroidView(
                factory = { mapView },
                update = { view ->
                    view.controller.setZoom(zoom)
                    view.overlays.clear()
                    val located = assets.filter { it.latitude != null && it.longitude != null }
                    located.forEach { asset ->
                        val marker = Marker(view).apply {
                            position = GeoPoint(asset.latitude!!, asset.longitude!!)
                            icon = markerDrawable(asset)
                            title = asset.shortName
                            snippet = "📶 ${asset.rssi} dBm"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { _, _ ->
                                navController.navigate(Routes.assetDetail(asset.id))
                                true
                            }
                        }
                        view.overlays.add(marker)
                    }
                    if (located.isNotEmpty()) {
                        view.controller.animateTo(
                            GeoPoint(
                                located.map { it.latitude!! }.average(),
                                located.map { it.longitude!! }.average()
                            )
                        )
                    }
                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

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
                        LegendDot(Color(0xFF2E7D32)); Spacer(Modifier.width(6.dp))
                        Text("Online", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(12.dp))
                        LegendDot(Color(0xFFC62828)); Spacer(Modifier.width(6.dp))
                        Text("Offline", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(12.dp))
                        LegendDot(Color(0xFFF9A825)); Spacer(Modifier.width(6.dp))
                        Text("Wartung", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Text(
                text = "⏱ $lastUpdate | 📍 ${assets.count { it.latitude != null }} sichtbar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape)
    )
}

private fun markerDrawable(asset: Asset): Drawable {
    val color = when (asset.status) {
        AssetStatus.ONLINE -> android.graphics.Color.rgb(46, 125, 50)
        AssetStatus.MAINTENANCE -> android.graphics.Color.rgb(249, 168, 37)
        AssetStatus.OFFLINE -> android.graphics.Color.rgb(198, 40, 40)
        AssetStatus.SEARCHING -> android.graphics.Color.rgb(21, 101, 192)
        AssetStatus.UNKNOWN -> android.graphics.Color.GRAY
    }
    val sizePx = 36
    return ShapeDrawable(OvalShape()).apply {
        paint.color = color
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
        setBounds(0, 0, sizePx, sizePx)
    }
}
