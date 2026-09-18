package com.secureguard.enterprise.presentation.ui.opscenter

/**
 * Sicherheitsregeln für die lokale Asset-Auslieferung (§29).
 *
 * Genau diese Logik wird vom [LocalAssetWebViewClient] aufgerufen und ist
 * dadurch isoliert unit-testbar. Es wird ausschließlich der feste Origin
 * `https://ops.secureguard.local/` bedient; alle anderen URLs werden nicht
 * ausgeliefert.
 */
object OpsCenterAssetGuard {

    const val ORIGIN = "https://ops.secureguard.local/"
    const val HOST = "ops.secureguard.local"

    /** True nur für den erlaubten Origin. */
    fun isTrustedOrigin(scheme: String?, host: String?): Boolean =
        scheme == "https" && host == HOST

    /**
     * True, wenn [name] ein sicherer relativer Asset-Pfad ist (kein
     * Pfad-Traversal, kein absoluter Pfad, kein Backslash, keine NUL-Bytes).
     */
    fun isSafeAssetPath(name: String): Boolean {
        if (name.contains("..")) return false
        if (name.startsWith("/") || name.startsWith("\\")) return false
        if (name.contains("\\")) return false
        if (name.contains('\u0000')) return false
        return true
    }
}
