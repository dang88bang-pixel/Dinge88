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

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.alerts_channel_desc) }

            val agent = NotificationChannel(
                CHANNEL_AGENT,
                context.getString(R.string.agent_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.agent_channel_desc) }

            val telemetry = NotificationChannel(
                CHANNEL_TELEMETRY,
                context.getString(R.string.telemetry_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.telemetry_channel_desc) }

            val system = NotificationChannel(
                CHANNEL_SYSTEM,
                context.getString(R.string.system_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.system_channel_desc) }

            notificationManager.createNotificationChannels(
                listOf(alerts, agent, telemetry, system)
            )
        }
    }

    /** Benachrichtigung nach einem abgeschlossenen Worker-Zyklus. */
    fun sendAgentCycleNotification(content: String) {
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
        val title = context.getString(
            if (success) R.string.notification_action_success else R.string.notification_action_failed
        )
        val body = context.getString(
            R.string.notification_action_body,
            actionType::class.simpleName ?: context.getString(R.string.fallback_action),
            asset.shortName
        )
        notify(System.currentTimeMillis().toInt(), title, body, CHANNEL_ALERTS)
    }

    fun sendAlertNotification(title: String, body: String) {
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
