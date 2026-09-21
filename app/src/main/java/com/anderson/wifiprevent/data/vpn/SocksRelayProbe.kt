package com.anderson.wifiprevent.data.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

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
            }
            SocksRelayStatus(
                available = true,
                message = "Relé SOCKS5 disponible en la PC."
            )
        }.getOrElse {
            SocksRelayStatus(
                available = false,
                message = "Relé SOCKS5 no disponible. Reinicia los servicios locales de la PC."
            )
        }
    }

    companion object {
        const val EMULATOR_HOST = "10.0.2.2"
        const val DEFAULT_PORT = 1080
        private const val TIMEOUT_MS = 2_000
        internal val GREETING = byteArrayOf(0x05, 0x01, 0x00)
        internal val ACCEPTED = byteArrayOf(0x05, 0x00)
    }
}
