package com.secureguard.enterprise.presentation.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Returns the set of runtime permissions that SecureGuard needs for its
 * detection and notification channels. The set adapts to the Android version:
 *  - Android 13+ uses POST_NOTIFICATIONS
 *  - Android 12+ uses the runtime BLUETOOTH_SCAN / BLUETOOTH_CONNECT permissions
 *    instead of location for BLE scanning
 */
fun requiredPermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_CONNECT
    }
    return perms.toTypedArray()
}

fun missingPermissions(context: Context): List<String> =
    requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

/**
 * Hintergrund-Standort (ab Android 10): wird für WiFi-/BLE-Scans des
 * Hintergrund-Agenten benötigt und **nur** separat angefragt, nachdem die
 * feine Standortfreigabe erteilt wurde (Voraussetzung des Systems).
 */
fun missingBackgroundPermissions(context: Context): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
    val fineGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (!fineGranted) return emptyList()
    val bgGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return if (bgGranted) emptyList()
    else listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
}
