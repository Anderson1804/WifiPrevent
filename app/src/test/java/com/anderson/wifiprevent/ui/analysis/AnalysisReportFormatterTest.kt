package com.anderson.wifiprevent.ui.analysis

import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.RelayCaptureMetrics
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisReportFormatterTest {
    @Test
    fun reportIncludesAggregatesAndPrivacyScope() {
        val report = formatAnalysisReport(entry(relayMetricsCollected = true))

        assertTrue(report.contains("Evaluación preliminar: Riesgo bajo"))
        assertTrue(report.contains("TLS/QUIC: 8"))
        assertTrue(report.contains("Destinos únicos: 3"))
        assertTrue(report.contains("Mantén HTTPS"))
        assertTrue(report.contains("No inspecciona contenido"))
        assertFalse(report.contains("192.0.2.1"))
    }

    @Test
    fun fullReportExplainsWhenRelaySnapshotIsUnavailable() {
        val report = formatAnalysisReport(entry(relayMetricsCollected = false))

        assertTrue(report.contains("Observaciones del relé: no disponibles"))
        assertFalse(report.contains("TLS/QUIC: 8"))
    }

    private fun entry(relayMetricsCollected: Boolean) = AnalysisHistoryEntry(
        id = "session-id",
        receivedAt = "2026-09-29T12:00:00Z",
        ssid = "Red de prueba",
        securityType = "WPA3_SAE",
        captivePortal = false,
        metrics = TrafficMetrics(60, 2_048, 1_024, 20, 10),
        metadata = TrafficMetadataSummary.EMPTY,
        riskLevel = "low",
        riskReasons = listOf("La red informa cifrado moderno."),
        assessmentScope = "connection_and_traffic_metadata",
        assessmentVersion = "rules-relay-v3",
        trafficAnalysisPerformed = true,
        captureMode = "full",
        indicators = emptyList(),
        sampleQuality = "adequate",
        recommendations = listOf("Mantén HTTPS."),
        relayMetricsCollected = relayMetricsCollected,
        relayMetrics = RelayCaptureMetrics(4, 6, 2, 0, 8, 0, 3)
    )
}
