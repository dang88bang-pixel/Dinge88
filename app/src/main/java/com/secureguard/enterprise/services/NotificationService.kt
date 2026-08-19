package com.secureguard.enterprise.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
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

    fun sendFoundNotification(asset: Asset, detection: Detection) {
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
    }

    private fun formatLocation(detection: Detection): String {
        return if (detection.latitude != null && detection.longitude != null) {
            "${detection.latitude}, ${detection.longitude}"
        } else {
            "Unbekannt"
        }
    }
}
