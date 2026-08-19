package com.secureguard.enterprise.presentation.ui.map

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.secureguard.enterprise.data.model.Asset

@SuppressLint("SetJavaScriptEnabled")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = hiltViewModel()
) {
    val assets by viewModel.assets.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Karte") }) }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(buildOsmUrl(assets))
                }
            },
            update = { webView ->
                // Beim Aktualisieren der Positionen die Karte neu laden.
                webView.loadUrl(buildOsmUrl(assets))
            }
        )
    }
}

/**
 * Erstellt eine OpenStreetMap-URL mit eingebetteter Karte und Markern.
 */
private fun buildOsmUrl(assets: List<Asset>): String {
    val positioned = assets.filter { it.latitude != null && it.longitude != null }
    if (positioned.isEmpty()) {
        return "https://www.openstreetmap.org/export/embed.html?bbox=6.5,50.9,7.2,51.4&layer=mapnik"
    }
    val markers = positioned.joinToString("\n") {
        "var marker${it.id.hashCode().and(0x7fffffff)} = L.marker([${it.latitude}, ${it.longitude}]).addTo(map).bindPopup('${it.shortName}');"
    }
    return "https://www.openstreetmap.org/export/embed.html?bbox=6.5,50.9,7.2,51.4&layer=mapnik" +
        "&marker=${positioned.first().latitude},${positioned.first().longitude}"
}
