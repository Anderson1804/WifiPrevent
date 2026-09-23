package com.anderson.wifiprevent.domain.model

data class RelayCaptureMetrics(
    val tcpConnections: Long,
    val udpDatagrams: Long,
    val dnsObservations: Long,
    val httpObservations: Long,
    val tlsOrQuicObservations: Long,
    val otherObservations: Long,
    val uniqueDestinations: Int
) {
    companion object {
        val EMPTY = RelayCaptureMetrics(0, 0, 0, 0, 0, 0, 0)
    }
}
