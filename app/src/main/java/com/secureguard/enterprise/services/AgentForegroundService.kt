package com.secureguard.enterprise.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Thin foreground wrapper around [AgentService]. Starting this service promotes
 * the agent to a foreground state (so Android does not kill it while the screen
 * is off) and keeps a persistent status notification.
 */
@AndroidEntryPoint
class AgentForegroundService : Service() {

    @Inject lateinit var agentService: AgentService
    @Inject lateinit var notificationService: NotificationService

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                agentService.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(
            NotificationService.AGENT_NOTIFICATION_ID,
            notificationService.buildAgentNotification("Agent wird initialisiert …")
        )
        agentService.start()
        return START_STICKY
    }

    override fun onDestroy() {
        agentService.stop()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.secureguard.enterprise.AGENT_START"
        const val ACTION_STOP = "com.secureguard.enterprise.AGENT_STOP"
    }
}
