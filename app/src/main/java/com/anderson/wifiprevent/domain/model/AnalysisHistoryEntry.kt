package com.anderson.wifiprevent.domain.model

data class AnalysisHistoryEntry(
    val id: String,
    val receivedAt: String,
    val ssid: String?,
    val securityType: String?,
    val metrics: TrafficMetrics
)

data class AnalysisHistoryPage(
    val entries: List<AnalysisHistoryEntry>,
    val nextBefore: String?
)
