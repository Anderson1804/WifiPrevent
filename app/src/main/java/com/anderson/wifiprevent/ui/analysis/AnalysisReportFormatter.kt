package com.anderson.wifiprevent.ui.analysis

import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.ui.common.formatAssessmentVersion
import com.anderson.wifiprevent.ui.common.formatRiskLevel
import com.anderson.wifiprevent.ui.common.formatSampleQuality
import com.anderson.wifiprevent.ui.common.formatSecurityType
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAnalysisReport(entry: AnalysisHistoryEntry): String = buildString {
    appendLine("WiFiPrevent - Resumen de análisis")
    appendLine("Red: ${entry.ssid ?: "Nombre no disponible"}")
    appendLine("Fecha registrada: ${formatReportDate(entry.receivedAt)}")
    appendLine("Seguridad: ${formatSecurityType(entry.securityType)}")
    appendLine(
        "Portal cautivo: " + when (entry.captivePortal) {
            true -> "detectado"
            false -> "no detectado"
            null -> "sin dato"
        }
    )
    appendLine("Modo: ${if (entry.captureMode == "full") "captura completa" else "validación controlada"}")
    appendLine("Evaluación preliminar: ${formatRiskLevel(entry.riskLevel, entry.assessmentScope != null)}")
    appendLine("Método: ${formatAssessmentVersion(entry.assessmentVersion)}")
    appendLine("Calidad de la muestra: ${formatSampleQuality(entry.sampleQuality)}")
    appendLine()
    appendLine("Métricas")
    appendLine("Duración: ${formatReportDuration(entry.metrics.durationSeconds)}")
    appendLine("Recibido: ${formatReportBytes(entry.metrics.receivedBytes)}")
    appendLine("Enviado: ${formatReportBytes(entry.metrics.transmittedBytes)}")
    appendLine("Paquetes recibidos: ${entry.metrics.receivedPackets}")
    appendLine("Paquetes enviados: ${entry.metrics.transmittedPackets}")

    if (entry.relayMetricsCollected) {
        appendLine()
        appendLine("Observaciones del relé")
        appendLine("TCP: ${entry.relayMetrics.tcpConnections}")
        appendLine("UDP: ${entry.relayMetrics.udpDatagrams}")
        appendLine("DNS: ${entry.relayMetrics.dnsObservations}")
        appendLine("Conexiones al puerto 80 (posible HTTP): ${entry.relayMetrics.httpObservations}")
        appendLine("TLS/QUIC: ${entry.relayMetrics.tlsOrQuicObservations}")
        appendLine("Otros: ${entry.relayMetrics.otherObservations}")
        appendLine("Destinos únicos: ${entry.relayMetrics.uniqueDestinations}")
    } else if (entry.captureMode == "full") {
        appendLine()
        appendLine("Observaciones del relé: no disponibles para esta sesión.")
    }

    if (entry.riskReasons.isNotEmpty()) {
        appendLine()
        appendLine("Fundamentos de la evaluación")
        entry.riskReasons.forEach { appendLine("• $it") }
    }
    if (entry.recommendations.isNotEmpty()) {
        appendLine()
        appendLine("Recomendaciones preventivas")
        entry.recommendations.forEach { appendLine("• $it") }
    }
    appendLine()
    append(
        "Resultado orientativo basado en metadatos. No inspecciona contenido ni confirma por sí solo una amenaza."
    )
}

private fun formatReportBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatReportDuration(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatReportDate(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("es")))
}.getOrDefault(value)
