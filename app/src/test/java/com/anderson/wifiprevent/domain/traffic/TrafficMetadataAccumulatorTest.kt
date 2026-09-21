package com.anderson.wifiprevent.domain.traffic

import org.junit.Assert.assertEquals
import org.junit.Test

class TrafficMetadataAccumulatorTest {
    @Test
    fun aggregatesProtocolsApplicationsAndUniqueDestinations() {
        val accumulator = TrafficMetadataAccumulator()

        accumulator.add(ipv4(6, 50_000, 443, 20))
        accumulator.add(ipv4(6, 50_001, 443, 20))
        accumulator.add(ipv4(17, 53_000, 53, 21))
        accumulator.add(byteArrayOf(0x30))

        val summary = accumulator.summary()
        assertEquals(3, summary.parsedPackets)
        assertEquals(1, summary.unparsedPackets)
        assertEquals(2, summary.tcpPackets)
        assertEquals(1, summary.udpPackets)
        assertEquals(2, summary.tlsOrQuicPackets)
        assertEquals(1, summary.dnsPackets)
        assertEquals(2, summary.uniqueDestinations)
    }

    private fun ipv4(
        protocol: Int,
        sourcePort: Int,
        destinationPort: Int,
        destinationLastByte: Int
    ): ByteArray {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[3] = 28
        packet[9] = protocol.toByte()
        byteArrayOf(10, 0, 0, 2).copyInto(packet, 12)
        byteArrayOf(203.toByte(), 0, 113, destinationLastByte.toByte()).copyInto(packet, 16)
        putU16(packet, 20, sourcePort)
        putU16(packet, 22, destinationPort)
        return packet
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
