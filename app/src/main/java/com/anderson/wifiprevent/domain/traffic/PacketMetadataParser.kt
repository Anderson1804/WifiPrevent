package com.anderson.wifiprevent.domain.traffic

import java.net.InetAddress

object PacketMetadataParser {
    fun parse(packet: ByteArray): PacketMetadata? {
        if (packet.isEmpty()) return null
        return when (packet[0].unsigned() ushr 4) {
            4 -> parseIpv4(packet)
            6 -> parseIpv6(packet)
            else -> null
        }
    }

    private fun parseIpv4(packet: ByteArray): PacketMetadata? {
        if (packet.size < IPV4_MIN_HEADER) return null
        val headerBytes = (packet[0].unsigned() and 0x0F) * 4
        if (headerBytes < IPV4_MIN_HEADER || packet.size < headerBytes) return null
        val totalBytes = packet.u16(2)
        if (totalBytes < headerBytes || totalBytes > packet.size) return null

        val protocolNumber = packet[9].unsigned()
        val fragmentOffset = packet.u16(6) and 0x1FFF
        val ports = if (fragmentOffset == 0) parsePorts(packet, headerBytes, totalBytes, protocolNumber)
            else null
        return metadata(
            ipVersion = 4,
            protocolNumber = protocolNumber,
            source = packet.copyOfRange(12, 16),
            destination = packet.copyOfRange(16, 20),
            ports = ports,
            payloadBytes = totalBytes - headerBytes
        )
    }

    private fun parseIpv6(packet: ByteArray): PacketMetadata? {
        if (packet.size < IPV6_HEADER) return null
        val payloadBytes = packet.u16(4)
        val totalBytes = IPV6_HEADER + payloadBytes
        if (totalBytes > packet.size) return null
        var protocolNumber = packet[6].unsigned()
        var transportOffset = IPV6_HEADER
        var firstFragment = true
        var extensionCount = 0
        while (protocolNumber in IPV6_EXTENSION_HEADERS) {
            if (++extensionCount > MAX_EXTENSION_HEADERS) return null
            when (protocolNumber) {
                0, 43, 60 -> {
                    if (transportOffset + 2 > totalBytes) return null
                    val next = packet[transportOffset].unsigned()
                    val length = (packet[transportOffset + 1].unsigned() + 1) * 8
                    if (length < 8 || transportOffset + length > totalBytes) return null
                    protocolNumber = next
                    transportOffset += length
                }
                44 -> {
                    if (transportOffset + 8 > totalBytes) return null
                    val next = packet[transportOffset].unsigned()
                    val fragmentField = packet.u16(transportOffset + 2)
                    firstFragment = fragmentField and 0xFFF8 == 0
                    protocolNumber = next
                    transportOffset += 8
                }
                51 -> {
                    if (transportOffset + 2 > totalBytes) return null
                    val next = packet[transportOffset].unsigned()
                    val length = (packet[transportOffset + 1].unsigned() + 2) * 4
                    if (length < 8 || transportOffset + length > totalBytes) return null
                    protocolNumber = next
                    transportOffset += length
                }
            }
        }
        val ports = if (firstFragment) {
            parsePorts(packet, transportOffset, totalBytes, protocolNumber)
        } else null
        return metadata(
            ipVersion = 6,
            protocolNumber = protocolNumber,
            source = packet.copyOfRange(8, 24),
            destination = packet.copyOfRange(24, 40),
            ports = ports,
            payloadBytes = payloadBytes
        )
    }

    private fun metadata(
        ipVersion: Int,
        protocolNumber: Int,
        source: ByteArray,
        destination: ByteArray,
        ports: Pair<Int, Int>?,
        payloadBytes: Int
    ): PacketMetadata {
        val protocol = when (protocolNumber) {
            6 -> TransportProtocol.TCP
            17 -> TransportProtocol.UDP
            1 -> TransportProtocol.ICMP
            58 -> TransportProtocol.ICMPV6
            else -> TransportProtocol.OTHER
        }
        val sourcePort = ports?.first
        val destinationPort = ports?.second
        return PacketMetadata(
            ipVersion = ipVersion,
            transportProtocol = protocol,
            sourceAddress = requireNotNull(InetAddress.getByAddress(source).hostAddress),
            destinationAddress = requireNotNull(InetAddress.getByAddress(destination).hostAddress),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            ipPayloadBytes = payloadBytes,
            applicationHint = applicationHint(protocol, sourcePort, destinationPort)
        )
    }

    private fun parsePorts(
        packet: ByteArray,
        offset: Int,
        totalBytes: Int,
        protocolNumber: Int
    ): Pair<Int, Int>? {
        if (protocolNumber != 6 && protocolNumber != 17) return null
        if (offset + 4 > totalBytes) return null
        return packet.u16(offset) to packet.u16(offset + 2)
    }

    private fun applicationHint(
        protocol: TransportProtocol,
        sourcePort: Int?,
        destinationPort: Int?
    ): ApplicationHint {
        val ports = setOfNotNull(sourcePort, destinationPort)
        return when {
            53 in ports && (protocol == TransportProtocol.TCP || protocol == TransportProtocol.UDP) ->
                ApplicationHint.DNS
            80 in ports && protocol == TransportProtocol.TCP -> ApplicationHint.HTTP
            443 in ports && (protocol == TransportProtocol.TCP || protocol == TransportProtocol.UDP) ->
                ApplicationHint.TLS_OR_QUIC
            else -> ApplicationHint.OTHER
        }
    }

    private fun Byte.unsigned(): Int = toInt() and 0xFF
    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].unsigned() shl 8) or this[offset + 1].unsigned()

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val MAX_EXTENSION_HEADERS = 8
    private val IPV6_EXTENSION_HEADERS = setOf(0, 43, 44, 51, 60)
}
