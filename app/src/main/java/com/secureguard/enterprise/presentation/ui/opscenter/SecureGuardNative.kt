package com.secureguard.enterprise.presentation.ui.opscenter

import android.webkit.JavascriptInterface

/**
 * `@JavascriptInterface`-Klasse, die der 3D-Oberfläche als `SecureGuardNative`
 * injiziert wird (§30/§31).
 *
 * Deckelt die WebView-API: JS ruft nur diese Methoden; alle Daten laufen als
 * JSON – niemals über URL-Parameter (die URL bleibt konstant
 * `https://ops.secureguard.local/`).
 */
class SecureGuardNative(
    private val onReadyCallback: () -> Unit,
    private val onEventCallback: (String) -> Unit,
    private val onSnapshotCallback: () -> Unit,
    private val onActionCallback: (String) -> Unit
) {

    @JavascriptInterface
    fun ready() {
        onReadyCallback()
    }

    @JavascriptInterface
    fun event(json: String?) {
        onEventCallback(json ?: "")
    }

    @JavascriptInterface
    fun snapshot() {
        onSnapshotCallback()
    }

    @JavascriptInterface
    fun action(json: String?) {
        onActionCallback(json ?: "")
    }
}
