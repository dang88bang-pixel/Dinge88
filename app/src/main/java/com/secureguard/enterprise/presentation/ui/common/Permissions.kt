package com.secureguard.enterprise.presentation.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Alle Runtime-Berechtigungen, die die fertige APK braucht – ohne künstliche
 * Einschränkung einzelner Kanäle (BLE, WiFi, GPS, Kamera, NFC, Speicher, Sensoren).
 */
fun requiredPermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        perms += Manifest.permission.ACCESS_BACKGROUND_LOCATION
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_CONNECT
        perms += Manifest.permission.BLUETOOTH_ADVERTISE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
        perms += Manifest.permission.NEARBY_WIFI_DEVICES
        perms += Manifest.permission.READ_MEDIA_IMAGES
        perms += Manifest.permission.READ_MEDIA_AUDIO
        perms += Manifest.permission.READ_MEDIA_VIDEO
    } else {
        perms += Manifest.permission.READ_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
    }
    return perms.distinct().toTypedArray()
}

fun missingPermissions(context: Context): List<String> =
    requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
