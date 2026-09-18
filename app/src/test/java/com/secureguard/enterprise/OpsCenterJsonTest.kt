package com.secureguard.enterprise

import com.secureguard.enterprise.presentation.ui.opscenter.OpsCenterAssetGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON-Bridge-Format und lokale Asset-Auslieferung (§29–§31):
 * - Der Snapshot wird als JSON transportiert – nie über URL-Parameter.
 * - Es wird nur der fixed Origin bedient, anderes wird nicht ausgeliefert.
 * - Pfad-Traversal wird deterministisch blockiert.
 */
class OpsCenterJsonTest {

    @Test
    fun onlyTheFixedOriginIsServed() {
        assertTrue(OpsCenterAssetGuard.isTrustedOrigin("https", "ops.secureguard.local"))
        assertFalse(OpsCenterAssetGuard.isTrustedOrigin("http", "ops.secureguard.local"))
        assertFalse(OpsCenterAssetGuard.isTrustedOrigin("https", "evil.example.com"))
        assertFalse(OpsCenterAssetGuard.isTrustedOrigin("file", ""))
        assertFalse(OpsCenterAssetGuard.isTrustedOrigin(null, null))
    }

    @Test
    fun pathTraversalIsBlocked() {
        assertFalse(OpsCenterAssetGuard.isSafeAssetPath("../secrets.properties"))
        assertFalse(OpsCenterAssetGuard.isSafeAssetPath("/etc/passwd"))
        assertFalse(OpsCenterAssetGuard.isSafeAssetPath("..\\windows\\path"))
        assertFalse(OpsCenterAssetGuard.isSafeAssetPath("a\\b"))
        assertFalse(OpsCenterAssetGuard.isSafeAssetPath("a\u0000b"))
        assertTrue(OpsCenterAssetGuard.isSafeAssetPath("index.html"))
        assertTrue(OpsCenterAssetGuard.isSafeAssetPath("assets/index-abc.js"))
    }

    @Test
    fun originConstantIsStable() {
        assertEquals("https://ops.secureguard.local/", OpsCenterAssetGuard.ORIGIN)
    }
}
