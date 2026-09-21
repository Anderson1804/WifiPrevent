package com.anderson.wifiprevent.domain.traffic

enum class TransportProtocol {
    TCP,
    UDP,
    ICMP,
    ICMPV6,
    OTHER
}

enum class ApplicationHint {
    DNS,
    HTTP,
    TLS_OR_QUIC,
    OTHER
}

data class PacketMetadata(
    val ipVersion: Int,
    val transportProtocol: TransportProtocol,
    val sourceAddress: String,
    val destinationAddress: String,
    val sourcePort: Int?,
    val destinationPort: Int?,
    val ipPayloadBytes: Int,
    val applicationHint: ApplicationHint
)
