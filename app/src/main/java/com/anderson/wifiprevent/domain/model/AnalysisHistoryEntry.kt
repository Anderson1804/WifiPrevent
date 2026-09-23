package com.anderson.wifiprevent.domain.model

import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary

data class AnalysisHistoryEntry(
    val id: String,
    val receivedAt: String,
    val ssid: String?,
    val securityType: String?,
    val captivePortal: Boolean?,
    val metrics: TrafficMetrics,
    val metadata: TrafficMetadataSummary,
    val riskLevel: String?,
    val riskReasons: List<String>,
    val assessmentScope: String?,
    val assessmentVersion: String?,
    val trafficAnalysisPerformed: Boolean,
    val captureMode: String,
    val indicators: List<TrafficIndicator>,
    val sampleQuality: String,
    val relayMetricsCollected: Boolean,
    val relayMetrics: RelayCaptureMetrics
)

data class AnalysisHistoryPage(
    val entries: List<AnalysisHistoryEntry>,
    val nextBefore: String?
)
