package com.anderson.wifiprevent.data.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.net.IpPrefix
import android.os.Build
import android.os.ParcelFileDescriptor
import com.anderson.wifiprevent.MainActivity
import com.anderson.wifiprevent.data.local.AnalysisSessionStore
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataAccumulator
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.domain.traffic.VpnCapturePlans
import java.io.FileInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class TrafficAnalysisService : VpnService() {
    private val sessionStore by lazy { AnalysisSessionStore(this) }
    private var tunnel: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null
    @Volatile private var capturing = false
    private var accumulator = TrafficMetadataAccumulator()
    private val packetForwarder: PacketForwarder by lazy {
        HevPacketForwarder(applicationContext)
    }

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
                stopControlledCapture()
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
                val mode = intent.getStringExtra(EXTRA_CAPTURE_MODE)
                    ?.let { value -> CaptureMode.entries.firstOrNull { it.apiValue == value } }
                    ?: CaptureMode.CONTROLLED
                sessionStore.start(sessionId, networkName, securityType, mode)
                startAsForeground(networkName)
                if (!startCapture(mode)) {
                    sessionStore.fail()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        stopControlledCapture()
        sessionStore.complete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        val wasCapturing = capturing
        stopControlledCapture()
        if (wasCapturing) sessionStore.complete()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopControlledCapture()
        sessionStore.complete()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun startCapture(mode: CaptureMode): Boolean = runCatching {
        stopControlledCapture()
        val plan = VpnCapturePlans.forMode(mode)
        if (plan.requiresPacketForwarder && !packetForwarder.available) return false
        accumulator = TrafficMetadataAccumulator()
        val builder = Builder()
            .setSession(plan.sessionName)
            .setMtu(1500)
            .addAddress(plan.ipv4Address, plan.ipv4PrefixLength)
            .setBlocking(true)
        plan.routes.forEach { route -> builder.addRoute(route.address, route.prefixLength) }
        if (mode == CaptureMode.FULL) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            builder
                .addDnsServer(FULL_DNS)
                .excludeRoute(
                    IpPrefix(InetAddress.getByName(SocksRelayProbe.EMULATOR_HOST), 32)
                )
        }
        val established = builder.establish() ?: return false
        tunnel = established
        capturing = true
        if (plan.requiresPacketForwarder) {
            if (!packetForwarder.start(established, ::recordPacket)) {
                stopControlledCapture()
                return false
            }
        } else {
            readerThread = Thread({ readPackets(established) }, "wifiprevent-tun-reader").apply {
                start()
            }
        }
        if (plan.generateValidationTraffic) {
            Thread({ generateControlledPackets() }, "wifiprevent-test-traffic").start()
        }
        true
    }.getOrDefault(false)

    private fun readPackets(descriptor: ParcelFileDescriptor) {
        val buffer = ByteArray(32_767)
        try {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (capturing) {
                    val length = input.read(buffer)
                    if (length <= 0) break
                    recordPacket(buffer.copyOf(length))
                }
            }
        } catch (_: IOException) {
            if (capturing) sessionStore.fail()
        }
    }

    private fun recordPacket(packet: ByteArray) {
        accumulator.add(packet)
        sessionStore.updateMetadata(accumulator.summary())
    }

    private fun generateControlledPackets() {
        val probes = listOf(
            Triple("203.0.113.1", 53, "dns"),
            Triple("203.0.113.2", 443, "tls"),
            Triple("203.0.113.3", 9_999, "other")
        )
        runCatching {
            DatagramChannel.open(StandardProtocolFamily.INET).use { channel ->
                channel.bind(
                    InetSocketAddress(InetAddress.getByName(TUN_ADDRESS), 0)
                )
                probes.forEach { (address, port, label) ->
                    val payload = "wifiprevent-$label".toByteArray(Charsets.UTF_8)
                    channel.send(
                        ByteBuffer.wrap(payload),
                        InetSocketAddress(InetAddress.getByName(address), port)
                    )
                }
            }
        }.onFailure { sessionStore.fail() }
    }

    private fun stopControlledCapture() {
        capturing = false
        packetForwarder.stop()
        runCatching { tunnel?.close() }
        tunnel = null
        readerThread?.interrupt()
        readerThread = null
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
        const val EXTRA_CAPTURE_MODE = "capture_mode"

        private const val CHANNEL_ID = "traffic_analysis"
        private const val NOTIFICATION_ID = 2001
        private const val TUN_ADDRESS = "10.77.0.2"
        private const val FULL_DNS = "1.1.1.1"
    }
}
