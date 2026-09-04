package com.secureguard.enterprise.presentation.ui.ops

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.theme.SurfaceDark
import org.json.JSONObject
import java.io.IOException

/** Virtueller Host, unter dem die gebauten Konsolen-Assets ausgeliefert werden. */
private const val ASSET_HOST = "ops.secureguard.local"
private const val ASSET_ROOT = "console3d"
private const val START_URL = "https://$ASSET_HOST/index.html"

/**
 * 3D Operations Center – vollflächiges Lagebild auf Basis von Three.js
 * (MIT-Lizenz, dauerhaft kostenfrei, keine Tier-Beschränkungen).
 *
 * Die Konsole läuft als lokales Web-Bundle im WebView; über
 * [OpsBridge] arbeitet sie direkt auf der verschlüsselten Room-Datenbank und
 * setzt Aktionen über den echten `AgentService` ab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsCenter3DScreen(
    navController: NavController,
    viewModel: OpsCenterViewModel = hiltViewModel()
) {
    val ready by viewModel.ready.collectAsState()
    val assetCount by viewModel.assetCount.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) { navController.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🛰️ 3D Operations Center · $assetCount Assets") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Lagebild neu laden")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    createOpsWebView(context, viewModel).also { webView = it }
                }
            )
            if (!ready) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createOpsWebView(context: Context, viewModel: OpsCenterViewModel): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.parseColor("#050D18"))
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Nur lokale Assets – kein Datei-/Content-Zugriff nötig.
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        }
        webViewClient = AssetWebViewClient(context.applicationContext)
        addJavascriptInterface(OpsBridge(viewModel, this), "SecureGuardNative")
        loadUrl(START_URL)
    }

/**
 * Liefert das Konsolen-Bundle unter einem echten https-Origin aus.
 *
 * Notwendig, weil ES-Module über `file://` von der CORS-Policy blockiert
 * werden. Bewusst ohne zusätzliche Abhängigkeit (kein androidx.webkit),
 * damit der Build schlank und reproduzierbar bleibt.
 */
private class AssetWebViewClient(private val appContext: Context) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val url: Uri = request.url
        if (!url.host.equals(ASSET_HOST, ignoreCase = true)) return null

        val path = url.path.orEmpty().let { if (it.isEmpty() || it == "/") "/index.html" else it }
        val assetPath = "$ASSET_ROOT${path}"
        return try {
            val stream = appContext.assets.open(assetPath.removePrefix("/").replace("//", "/"))
            WebResourceResponse(
                mimeTypeOf(path),
                "utf-8",
                200,
                "OK",
                mapOf(
                    "Cache-Control" to "no-store",
                    "Access-Control-Allow-Origin" to "*"
                ),
                stream
            )
        } catch (_: IOException) {
            null
        }
    }

    /** Externe Links nicht im Lagebild öffnen. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !request.url.host.equals(ASSET_HOST, ignoreCase = true)

    private fun mimeTypeOf(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".js") -> "text/javascript"
        path.endsWith(".mjs") -> "text/javascript"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".woff2") -> "font/woff2"
        else -> "application/octet-stream"
    }
}

/**
 * JavaScript-Brücke `window.SecureGuardNative`.
 *
 * Alle Methoden sind kurz und nicht blockierend: Lesezugriffe liefern den
 * vorbereiteten Schnappschuss, Schreibzugriffe starten eine Coroutine und
 * melden das Ergebnis über `window.SecureGuardBridgeResolve(...)` zurück.
 */
private class OpsBridge(
    private val viewModel: OpsCenterViewModel,
    private val webView: WebView
) {

    @JavascriptInterface
    fun snapshot(): String = viewModel.snapshotJson()

    @JavascriptInterface
    fun toggleAgent() {
        webView.post { viewModel.toggleAgent() }
    }

    @JavascriptInterface
    fun acknowledgeAlert(id: String) {
        val parsed = id.toLongOrNull() ?: return
        webView.post { viewModel.acknowledgeAlert(parsed) }
    }

    @JavascriptInterface
    fun execute(requestId: String, assetId: String, wireCommand: String, note: String) {
        webView.post {
            viewModel.execute(assetId, wireCommand, note) { state, detail ->
                resolve(requestId, state, detail)
            }
        }
    }

    @JavascriptInterface
    fun flushQueue(requestId: String) {
        webView.post {
            viewModel.flushQueue { state, detail -> resolve(requestId, state, detail) }
        }
    }

    private fun resolve(requestId: String, state: String, detail: String) {
        val script = "window.SecureGuardBridgeResolve(" +
            "${JSONObject.quote(requestId)}," +
            "${JSONObject.quote(state)}," +
            "${JSONObject.quote(detail)})"
        webView.post { webView.evaluateJavascript(script, null) }
    }
}
