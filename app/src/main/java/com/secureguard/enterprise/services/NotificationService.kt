package com.secureguard.enterprise.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.secureguard.enterprise.R
import com.secureguard.enterprise.data.model.Asset
import com.secureguard.enterprise.data.model.Detection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet die Benachrichtigungen für den Agenten.
 */
@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "secureguard_channel"

    init {
        val channel = NotificationChannel(
            channelId,
            "SecureGuard Benachrichtigungen",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun sendActionNotification(asset: Asset, actionName: String, success: Boolean) {
        if (!canNotify()) return
        val title = if (success) "✅ ${asset.shortName}" else "❌ ${asset.shortName}"
        val content = "${asset.shortName}: Aktion '$actionName' " +
            (if (success) "erfolgreich ausgeführt." else "fehlgeschlagen.")
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        notificationManager.notify("${asset.id}-$actionName".hashCode(), notification)
        vibrate(asset.vibration)
    }

    fun sendFoundNotification(asset: Asset, detection: Detection) {
        if (!canNotify()) return
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("🛡️ ${asset.shortName} gefunden!")
            .setContentText(
                "📍 Standort: ${formatLocation(detection)} | " +
                    "📶 RSSI: ${detection.rssi} dBm"
            )
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(asset.id.hashCode(), notification)
        vibrate(asset.vibration)
    }

    private fun canNotify(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /** Löst Vibration aus, wenn das Asset es wünscht. */
    private fun vibrate(enabled: Boolean) {
        if (!enabled) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    private fun formatLocation(detection: Detection): String {
        return if (detection.latitude != null && detection.longitude != null) {
            "${detection.latitude}, ${detection.longitude}"
        } else {
            "Unbekannt"
        }
    }
}
