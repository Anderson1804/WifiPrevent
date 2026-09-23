package com.anderson.wifiprevent.data.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.io.InputStream
import hev.htproxy.TProxyService

data class SocksRelayStatus(
    val available: Boolean,
    val message: String
)

class SocksRelayProbe(
    private val host: String = EMULATOR_HOST,
    private val port: Int = DEFAULT_PORT
) {
    suspend fun check(): SocksRelayStatus = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                socket.getOutputStream().apply {
                    write(GREETING)
                    flush()
                }
                val response = socket.getInputStream().readNBytes(2)
                if (!response.contentEquals(ACCEPTED)) {
                    throw IOException("Respuesta SOCKS5 no compatible")
                }
                socket.getOutputStream().apply {
                    write(UDP_ASSOCIATE_REQUEST)
                    flush()
                }
                if (!validateUdpAssociateReply(readUdpAssociateReply(socket.getInputStream()))) {
                    throw IOException("El relé no admite asociación UDP")
                }
            }
            TProxyService.TProxyIsRunning()
            SocksRelayStatus(
                available = true,
                message = "Relé SOCKS5 TCP/UDP y motor nativo disponibles."
            )
        }.getOrElse {
            SocksRelayStatus(
                available = false,
                message = if (it is UnsatisfiedLinkError) {
                    "El relé responde, pero el motor nativo no pudo cargarse."
                } else {
                    "Relé SOCKS5 no disponible. Reinicia los servicios locales de la PC."
                }
            )
        }
    }

    companion object {
        const val EMULATOR_HOST = "10.0.2.2"
        const val DEFAULT_PORT = 1080
        private const val TIMEOUT_MS = 2_000
        internal val GREETING = byteArrayOf(0x05, 0x01, 0x00)
        internal val ACCEPTED = byteArrayOf(0x05, 0x00)
        internal val UDP_ASSOCIATE_REQUEST = byteArrayOf(
            0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
    }
}

private fun readUdpAssociateReply(input: InputStream): ByteArray {
    val header = input.readNBytes(4)
    if (header.size != 4) return header
    val tail = when (header[3].toInt() and 0xFF) {
        1 -> input.readNBytes(6)
        4 -> input.readNBytes(18)
        3 -> {
            val length = input.read()
            if (length < 0) return header
            byteArrayOf(length.toByte()) + input.readNBytes(length + 2)
        }
        else -> byteArrayOf()
    }
    return header + tail
}

internal fun validateUdpAssociateReply(response: ByteArray): Boolean {
    if (response.size < 4 ||
        response[0] != 0x05.toByte() ||
        response[1] != 0x00.toByte() ||
        response[2] != 0x00.toByte()
    ) return false
    val expectedSize = when (response[3].toInt() and 0xFF) {
        1 -> 10
        4 -> 22
        3 -> if (response.size >= 5) 7 + (response[4].toInt() and 0xFF) else return false
        else -> return false
    }
    return response.size == expectedSize
}
