package com.anderson.wifiprevent.data.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import hev.htproxy.TProxyService

interface PacketForwarder {
    val available: Boolean
    val unavailableReason: String?

    fun start(
        tunnel: ParcelFileDescriptor,
        onPacketObserved: (ByteArray) -> Unit
    ): Boolean
    fun stop()
}

class PendingPacketForwarder : PacketForwarder {
    override val available = false
    override val unavailableReason =
        "El motor de reenvío todavía no está incorporado. Se mantiene la captura controlada."

    override fun start(
        tunnel: ParcelFileDescriptor,
        onPacketObserved: (ByteArray) -> Unit
    ): Boolean = false
    override fun stop() = Unit
}

class HevPacketForwarder(
    private val context: Context,
    private val config: HevTunnelConfig = HevTunnelConfig()
) : PacketForwarder {
    override val available: Boolean
        get() = runCatching { TProxyService.TProxyIsRunning() }.isSuccess

    override val unavailableReason: String?
        get() = if (available) null else {
            "La biblioteca nativa de reenvío no está disponible para este dispositivo."
        }

    override fun start(
        tunnel: ParcelFileDescriptor,
        onPacketObserved: (ByteArray) -> Unit
    ): Boolean {
        if (!available || TProxyService.TProxyIsRunning()) return false
        val configFile = config.writeTo(context.filesDir)
        return runCatching {
            TProxyService.TProxyStartService(configFile.absolutePath, tunnel.fd)
        }.getOrDefault(false)
    }

    override fun stop() {
        runCatching {
            if (TProxyService.TProxyIsRunning()) {
                TProxyService.TProxyStopService()
            }
        }
    }
}
