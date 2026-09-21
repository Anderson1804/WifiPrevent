package com.anderson.wifiprevent.domain.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketMetadataParserTest {
    @Test
    fun parsesIpv4TcpAndHttpWithoutReadingPayload() {
        val packet = ipv4(protocol = 6, sourcePort = 49_152, destinationPort = 80)

        val result = PacketMetadataParser.parse(packet)!!

        assertEquals(4, result.ipVersion)
        assertEquals(TransportProtocol.TCP, result.transportProtocol)
        assertEquals("192.0.2.10", result.sourceAddress)
        assertEquals("198.51.100.20", result.destinationAddress)
        assertEquals(49_152, result.sourcePort)
        assertEquals(80, result.destinationPort)
        assertEquals(ApplicationHint.HTTP, result.applicationHint)
    }

    @Test
    fun parsesIpv4UdpDns() {
        val result = PacketMetadataParser.parse(
            ipv4(protocol = 17, sourcePort = 53, destinationPort = 53_000)
        )!!

        assertEquals(TransportProtocol.UDP, result.transportProtocol)
        assertEquals(ApplicationHint.DNS, result.applicationHint)
        assertEquals(8, result.ipPayloadBytes)
    }

    @Test
    fun parsesIpv6UdpQuic() {
        val result = PacketMetadataParser.parse(ipv6Udp(50_000, 443))!!

        assertEquals(6, result.ipVersion)
        assertEquals(TransportProtocol.UDP, result.transportProtocol)
        assertEquals("2001:db8:0:0:0:0:0:1", result.sourceAddress)
        assertEquals("2001:db8:0:0:0:0:0:2", result.destinationAddress)
        assertEquals(ApplicationHint.TLS_OR_QUIC, result.applicationHint)
    }

    @Test
    fun walksIpv6ExtensionHeaderBeforeUdp() {
        val packet = ipv6Udp(53_000, 53, withDestinationOptions = true)

        val result = PacketMetadataParser.parse(packet)!!

        assertEquals(TransportProtocol.UDP, result.transportProtocol)
        assertEquals(53, result.destinationPort)
        assertEquals(ApplicationHint.DNS, result.applicationHint)
    }

    @Test
    fun fragmentedIpv4PacketDoesNotInventPorts() {
        val packet = ipv4(protocol = 6, sourcePort = 1000, destinationPort = 443)
        packet[7] = 1

        val result = PacketMetadataParser.parse(packet)!!

        assertNull(result.sourcePort)
        assertNull(result.destinationPort)
        assertEquals(ApplicationHint.OTHER, result.applicationHint)
    }

    @Test
    fun rejectsUnsupportedAndTruncatedPackets() {
        assertNull(PacketMetadataParser.parse(byteArrayOf()))
        assertNull(PacketMetadataParser.parse(byteArrayOf(0x30)))
        assertNull(PacketMetadataParser.parse(byteArrayOf(0x45, 0, 0, 40)))
        assertNull(PacketMetadataParser.parse(ipv4(6, 1, 2).copyOf(22)))
    }

    private fun ipv4(protocol: Int, sourcePort: Int, destinationPort: Int): ByteArray {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[2] = 0
        packet[3] = 28
        packet[9] = protocol.toByte()
        byteArrayOf(192.toByte(), 0, 2, 10).copyInto(packet, 12)
        byteArrayOf(198.toByte(), 51, 100, 20).copyInto(packet, 16)
        putU16(packet, 20, sourcePort)
        putU16(packet, 22, destinationPort)
        return packet
    }

    private fun ipv6Udp(
        sourcePort: Int,
        destinationPort: Int,
        withDestinationOptions: Boolean = false
    ): ByteArray {
        val extensionBytes = if (withDestinationOptions) 8 else 0
        val packet = ByteArray(48 + extensionBytes)
        packet[0] = 0x60
        packet[5] = (8 + extensionBytes).toByte()
        packet[6] = if (withDestinationOptions) 60 else 17
        val source = byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
        val destination = source.copyOf().apply { this[15] = 2 }
        source.copyInto(packet, 8)
        destination.copyInto(packet, 24)
        if (withDestinationOptions) {
            packet[40] = 17
            packet[41] = 0
        }
        putU16(packet, 40 + extensionBytes, sourcePort)
        putU16(packet, 42 + extensionBytes, destinationPort)
        return packet
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 8).toByte()
        target[offset + 1] = value.toByte()
    }
}
