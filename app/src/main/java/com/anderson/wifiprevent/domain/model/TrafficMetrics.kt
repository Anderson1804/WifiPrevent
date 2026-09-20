package com.anderson.wifiprevent.domain.model

data class TrafficMetrics(
    val durationSeconds: Long,
    val receivedBytes: Long,
    val transmittedBytes: Long,
    val receivedPackets: Long,
    val transmittedPackets: Long
) {
    companion object {
        val EMPTY = TrafficMetrics(0, 0, 0, 0, 0)
    }
}
