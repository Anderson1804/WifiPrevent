package com.anderson.wifiprevent.data.vpn

import android.os.ParcelFileDescriptor

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
