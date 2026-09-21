package com.anderson.wifiprevent.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import com.anderson.wifiprevent.MainActivity
import com.anderson.wifiprevent.data.local.AnalysisSessionStore

class TrafficAnalysisService : VpnService() {
    private val sessionStore by lazy { AnalysisSessionStore(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                sessionStore.complete()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                START_NOT_STICKY
            }

            else -> {
                val networkName = intent?.getStringExtra(EXTRA_NETWORK_NAME)
                val securityType = intent?.getStringExtra(EXTRA_SECURITY_TYPE)
                val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
                    ?: return START_NOT_STICKY
                sessionStore.start(sessionId, networkName, securityType)
                startAsForeground(networkName)
                START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        sessionStore.complete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    private fun startAsForeground(networkName: String?) {
        val notification = createNotification(networkName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(networkName: String?): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, TrafficAnalysisService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("WiFiPrevent: sesión activa")
            .setContentText(
                networkName?.let { "Preparando análisis en $it" }
                    ?: "Preparando análisis de red"
            )
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Detener",
                    stopPendingIntent
                ).build()
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Sesiones de análisis",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantiene visible una sesión autorizada de WiFiPrevent."
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START =
            "com.anderson.wifiprevent.action.START_TRAFFIC_ANALYSIS"
        const val ACTION_STOP =
            "com.anderson.wifiprevent.action.STOP_TRAFFIC_ANALYSIS"
        const val EXTRA_NETWORK_NAME = "network_name"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SECURITY_TYPE = "security_type"

        private const val CHANNEL_ID = "traffic_analysis"
        private const val NOTIFICATION_ID = 2001
    }
}
