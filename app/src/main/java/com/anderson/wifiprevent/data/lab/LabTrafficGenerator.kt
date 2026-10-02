package com.anderson.wifiprevent.data.lab

import com.anderson.wifiprevent.domain.traffic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import java.util.UUID

data class LabResult(val executionId: String, val planned: Int, val received: Int)

class LabTrafficGenerator {
    suspend fun run(host: String, profile: LabProfile, relayHost: String): LabResult = withContext(Dispatchers.IO) {
        require(isPrivateLabAddress(host)) { "Usa una dirección IPv4 privada de tu laboratorio." }
        val canonicalHost = host.split('.').joinToString(".") { it.toInt().toString() }
        require(canonicalHost != relayHost) { "El destino debe usar una IP distinta del relé, excluida de la captura." }
        val execution = "lab-" + UUID.randomUUID().toString().replace("-", "").take(16)
        val offsets = labOffsets(profile)
        val start = System.nanoTime()
        var received = 0
        var lastAttempt: Long? = null
        for ((index, offset) in offsets.withIndex()) {
            currentCoroutineContext().ensureActive()
            val wait = offset - (System.nanoTime() - start) / 1_000_000
            val rateWait = lastAttempt?.let { 500 - (System.nanoTime() - it) / 1_000_000 } ?: 0
            if (maxOf(wait, rateWait) > 0) delay(maxOf(wait, rateWait))
            lastAttempt = System.nanoTime()
            currentCoroutineContext().ensureActive()
            // New socket per action; this is a lab generator, not traffic inspection.
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(InetAddress.getByAddress(canonicalHost.split('.').map { it.toInt().toByte() }.toByteArray()), 8765), 3_000)
                    socket.soTimeout = 3_000
                    val size = if (profile == LabProfile.UPLOAD) 2 * 1024 * 1024 else 0
                    val header = "POST /lab/$execution/$index HTTP/1.1\r\nHost: lab\r\n" +
                        "Connection: close\r\nContent-Length: $size\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(header.toByteArray(Charsets.US_ASCII))
                        if (size > 0) {
                            val data = ByteArray(8192)
                            repeat(size / data.size) { currentCoroutineContext().ensureActive(); write(data) }
                        }
                        flush()
                    }
                    val response = socket.getInputStream().bufferedReader(Charsets.US_ASCII).readLine()
                    if (response?.startsWith("HTTP/1.1 204") == true) received++
                }
            } catch (_: java.io.IOException) {
                // Reference comes from the receiver, not merely from the planned schedule.
            }
        }
        LabResult(execution, offsets.size, received)
    }
}
