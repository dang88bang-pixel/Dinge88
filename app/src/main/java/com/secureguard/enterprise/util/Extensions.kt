package com.secureguard.enterprise.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Komfort-Erweiterungen für die App.
 */

fun Date.formatTime(pattern: String = "HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(this)

fun Date.formatDateTime(pattern: String = "dd.MM.yyyy HH:mm"): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(this)

/** Prüft, ob die App Zugriff auf Bluetooth-Scan hat (API-abhängig). */
fun Context.hasBleScanPermission(): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Manifest.permission.BLUETOOTH_SCAN
    } else {
        Manifest.permission.BLUETOOTH
    }
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/** Prüft, ob die App Zugriff auf den Standort hat. */
fun Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

/** Prüft, ob Benachrichtigungen erlaubt sind (API 33+). */
fun Context.canPostNotifications(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}
