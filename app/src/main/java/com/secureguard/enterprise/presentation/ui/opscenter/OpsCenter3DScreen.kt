package com.secureguard.enterprise.presentation.ui.opscenter

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.secureguard.enterprise.presentation.designsystem.SgIconButton

/**
 * 3D Operations Center (§24–§31).
 *
 * WebView mit JavaScript + DOM-Storage, lokaler Asset-Auslieferung nur für den
 * Origin `https://ops.secureguard.local/`. `shouldInterceptRequest` serviert
 * ausschließlich Dateien aus `app/src/main/assets/console3d/` und blockiert
 * Pfad-Traversal; alle anderen Origins werden NICHT ausgeliefert (§29).
 *
 * Im Hintergrund wird die WebView pausiert, damit sie nicht dauerhaft CPU
 * zieht (§35).
 */
object OpsCenterWeb {
    const val ORIGIN = "https://ops.secureguard.local/"
    const val HOST = "ops.secureguard.local"
    const val ENTRY_NAME = "index.html"
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsCenter3DScreen(
    navController: NavController,
    viewModel: OpsCenterViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember { createOpsWebView(context, viewModel) }

    // Lifecycle: WebView pausieren/resumieren (§35).
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    webView.onResume()
                    webView.resumeTimers()
                }
                Lifecycle.Event.ON_STOP -> {
                    webView.onPause()
                    webView.pauseTimers()
                }
                Lifecycle.Event.ON_DESTROY -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.onPause()
        }
    }

    // Bridge-Flush: vom ViewModel enqueued JS-Aufrufe ausführen.
    LaunchedEffect(viewModel, webView) {
        viewModel.pendingBridgeActions.collect { _ ->
            val pending = viewModel.takePendingBridgeActions()
            pending.forEach { (_, json) ->
                val safe = json.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                val script = "window.SecureGuardOps && window.SecureGuardOps.ingest('$safe')"
                webView.evaluateJavascript(script, null)
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else navController.navigateUp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D Operations Center") },
                navigationIcon = {
                    SgIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        onClick = { navController.navigateUp() }
                    )
                },
                actions = {
                    SgIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Neu laden",
                        onClick = {
                            viewModel.refresh()
                            webView.reload()
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Baut die WebView einmalig mit sicheren Einstellungen + Bridge auf. */
@SuppressLint("SetJavaScriptEnabled")
private fun createOpsWebView(context: Context, viewModel: OpsCenterViewModel): WebView {
    val wv = WebView(context)
    val settings: WebSettings = wv.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = false
    settings.allowFileAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.allowContentAccess = false
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mediaPlaybackRequiresUserGesture = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

    val native = SecureGuardNative(
        onReadyCallback = { viewModel.onJsReady() },
        onEventCallback = { json -> viewModel.onJsEvent(json) },
        onSnapshotCallback = { viewModel.onJsReady() },
        onActionCallback = { json -> viewModel.handleBridgeAction(json) }
    )
    wv.addJavascriptInterface(native, "SecureGuardNative")

    wv.webViewClient = LocalAssetWebViewClient(context)

    wv.loadUrl(OpsCenterWeb.ORIGIN)
    return wv
}

/**
 * Bedient nur den fixed Origin `https://ops.secureguard.local/` aus den lokalen
 * Assets. Für alle anderen Origins wird `null` zurückgegeben – die Anforderung
 * wird NICHT blind ausgeliefert. Pfad-Traversal wird abgeblockt (§29).
 */
class LocalAssetWebViewClient(
    private val context: Context
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return super.shouldInterceptRequest(view, request)
        if (!OpsCenterAssetGuard.isTrustedOrigin(url.scheme, url.host)) {
            return null
        }
        return serve(url.path.orEmpty())
    }

    private fun serve(path: String): WebResourceResponse? {
        val relative = path.trimStart('/')
        val name = if (relative.isBlank()) OpsCenterWeb.ENTRY_NAME else relative

        // Pfad-Traversal verhindern (gemeinsame, testbare Regel).
        if (!OpsCenterAssetGuard.isSafeAssetPath(name)) return null

        return try {
            val stream = context.assets.open("console3d/$name")
            val ext = name.substringAfterLast('.', "html").lowercase()
            val mime = MIME_TYPES[ext] ?: "application/octet-stream"
            WebResourceResponse(
                mime,
                if (mime.startsWith("text/") || mime == "application/javascript") "utf-8" else null,
                200,
                "OK",
                emptyMap(),
                stream
            )
        } catch (_: Exception) {
            null
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url ?: return false
        return !OpsCenterAssetGuard.isTrustedOrigin(url.scheme, url.host)
    }

    private companion object {
        val MIME_TYPES = mapOf(
            "html" to "text/html",
            "js" to "application/javascript",
            "mjs" to "application/javascript",
            "css" to "text/css",
            "json" to "application/json",
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "svg" to "image/svg+xml",
            "woff" to "font/woff",
            "woff2" to "font/woff2"
        )
    }
}
