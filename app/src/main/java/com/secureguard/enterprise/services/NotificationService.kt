package com.secureguard.enterprise.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.secureguard.enterprise.MainActivity
import com.secureguard.enterprise.R
import com.secureguard.enterprise.data.model.Asset
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralised notification helper. Creates the two channels used by the app
 * (alerts & agent status) and exposes small, typed methods for the services
 * and ViewModels.
 */
@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val settingsPrefs =
        context.getSharedPreferences("secureguard_settings", Context.MODE_PRIVATE)

    init {
        createChannels()
    }

    /** Einstellung „Push-Benachrichtigungen“ (aus der Settings-UI). */
    private fun notificationsEnabledByUser(): Boolean =
        settingsPrefs.getBoolean("notifications", true)

    /** POST_NOTIFICATIONS-Berechtigung (Android 13+) prüfen. */
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun canSendNotifications(): Boolean =
        notificationsEnabledByUser() && hasNotificationPermission()

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                "Sicherheitsalarme",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Alarme und sicherheitsrelevante Ereignisse" }

            val agent = NotificationChannel(
                CHANNEL_AGENT,
                "Agent-Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Statusmeldungen des selbstlernenden Agenten" }

            val telemetry = NotificationChannel(
                CHANNEL_TELEMETRY,
                "Telemetriedaten",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Fahrzeug-/Asset-Telemetrie" }

            val system = NotificationChannel(
                CHANNEL_SYSTEM,
                "Systemmeldungen",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Systembenachrichtigungen (Backup, Sync, Fehler)" }

            notificationManager.createNotificationChannels(
                listOf(alerts, agent, telemetry, system)
            )
        }
    }

    /** Benachrichtigung nach einem abgeschlossenen Worker-Zyklus. */
    fun sendAgentCycleNotification(content: String) {
        if (!canSendNotifications()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setContentTitle("🛡️ SecureGuard Agent")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }

    fun sendActionNotification(asset: Asset, actionType: Any, success: Boolean) {
        if (!canSendNotifications()) return
        val title = if (success) "Aktion ausgeführt" else "Aktion fehlgeschlagen"
        val body = "${actionType::class.simpleName ?: "Aktion"} · ${asset.shortName}"
        notify(System.currentTimeMillis().toInt(), title, body, CHANNEL_ALERTS)
    }

    fun sendAlertNotification(title: String, body: String) {
        if (!canSendNotifications()) return
        notify(System.currentTimeMillis().toInt(), title, body, CHANNEL_ALERTS)
    }

    fun buildAgentNotification(content: String): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_AGENT)
            .setContentTitle("🛡️ SecureGuard Agent")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun notify(id: Int, title: String, body: String, channel: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_shield)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(id, notification)
    }

    companion object {
        const val CHANNEL_ALERTS = "secureguard_alerts"
        const val CHANNEL_AGENT = "secureguard_agent"
        const val CHANNEL_TELEMETRY = "secureguard_telemetry"
        const val CHANNEL_SYSTEM = "secureguard_system"
        const val AGENT_NOTIFICATION_ID = 1001
    }
}
