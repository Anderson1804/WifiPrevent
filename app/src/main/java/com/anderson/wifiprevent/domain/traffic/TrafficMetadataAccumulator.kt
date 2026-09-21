package com.anderson.wifiprevent.domain.traffic

import java.security.MessageDigest

data class TrafficMetadataSummary(
    val parsedPackets: Long,
    val unparsedPackets: Long,
    val ipv4Packets: Long,
    val ipv6Packets: Long,
    val tcpPackets: Long,
    val udpPackets: Long,
    val icmpPackets: Long,
    val otherTransportPackets: Long,
    val dnsPackets: Long,
    val httpPackets: Long,
    val tlsOrQuicPackets: Long,
    val uniqueDestinations: Int
) {
    companion object {
        val EMPTY = TrafficMetadataSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}

class TrafficMetadataAccumulator {
    private var parsedPackets = 0L
    private var unparsedPackets = 0L
    private var ipv4Packets = 0L
    private var ipv6Packets = 0L
    private var tcpPackets = 0L
    private var udpPackets = 0L
    private var icmpPackets = 0L
    private var otherTransportPackets = 0L
    private var dnsPackets = 0L
    private var httpPackets = 0L
    private var tlsOrQuicPackets = 0L
    private val destinationFingerprints = mutableSetOf<String>()

    @Synchronized
    fun add(packet: ByteArray) {
        val metadata = PacketMetadataParser.parse(packet)
        if (metadata == null) {
            unparsedPackets++
            return
        }
        parsedPackets++
        if (metadata.ipVersion == 4) ipv4Packets++ else ipv6Packets++
        when (metadata.transportProtocol) {
            TransportProtocol.TCP -> tcpPackets++
            TransportProtocol.UDP -> udpPackets++
            TransportProtocol.ICMP, TransportProtocol.ICMPV6 -> icmpPackets++
            TransportProtocol.OTHER -> otherTransportPackets++
        }
        when (metadata.applicationHint) {
            ApplicationHint.DNS -> dnsPackets++
            ApplicationHint.HTTP -> httpPackets++
            ApplicationHint.TLS_OR_QUIC -> tlsOrQuicPackets++
            ApplicationHint.OTHER -> Unit
        }
        destinationFingerprints += fingerprint(metadata.destinationAddress)
    }

    @Synchronized
    fun summary(): TrafficMetadataSummary = TrafficMetadataSummary(
        parsedPackets = parsedPackets,
        unparsedPackets = unparsedPackets,
        ipv4Packets = ipv4Packets,
        ipv6Packets = ipv6Packets,
        tcpPackets = tcpPackets,
        udpPackets = udpPackets,
        icmpPackets = icmpPackets,
        otherTransportPackets = otherTransportPackets,
        dnsPackets = dnsPackets,
        httpPackets = httpPackets,
        tlsOrQuicPackets = tlsOrQuicPackets,
        uniqueDestinations = destinationFingerprints.size
    )

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
