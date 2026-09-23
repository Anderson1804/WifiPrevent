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
    fun counters(): TunnelTrafficCounters?
}

data class TunnelTrafficCounters(
    val transmittedPackets: Long,
    val transmittedBytes: Long,
    val receivedPackets: Long,
    val receivedBytes: Long
)

internal fun nativeCounters(values: LongArray): TunnelTrafficCounters? {
    if (values.size != 4 || values.any { it < 0 }) return null
    return TunnelTrafficCounters(values[0], values[1], values[2], values[3])
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
    override fun counters(): TunnelTrafficCounters? = null
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

    override fun counters(): TunnelTrafficCounters? = runCatching {
        if (TProxyService.TProxyIsRunning()) {
            nativeCounters(TProxyService.TProxyGetStats())
        } else {
            null
        }
    }.getOrNull()
}
