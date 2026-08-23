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

/** Returns a full completeness report for the agent to guarantee 100% functional availability. */
fun permissionCompletenessReport(context: Context): Map<String, Boolean> {
    return requiredPermissions().associateWith {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Returns true only when ALL required permissions are granted (100% ready). */
fun allPermissionsGranted(context: Context): Boolean =
    missingPermissions(context).isEmpty()
