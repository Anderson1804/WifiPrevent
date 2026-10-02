package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.data.vpn.SocksRelayStatus
import com.anderson.wifiprevent.domain.model.*
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.ui.common.*

@Composable
fun AnalysisScreen(
    wifi: WifiSnapshot?, session: AnalysisSession?, metrics: TrafficMetrics,
    metadata: TrafficMetadataSummary, receipt: AnalysisReceipt?,
    uploadMessage: String?, uploadError: Boolean, uploading: Boolean,
    relayStatus: SocksRelayStatus?, checkingRelay: Boolean, fullModeSupported: Boolean,
    onHistory: () -> Unit, onPrepare: () -> Unit,
    onStartControlled: () -> Unit, onStartFull: () -> Unit, onStop: () -> Unit,
    onRetryUpload: () -> Unit, onCheckRelay: () -> Unit, modifier: Modifier = Modifier
) {
    val state = session?.state
    val ready = state == AnalysisSessionState.READY
    val completed = state == AnalysisSessionState.COMPLETED
    val active = state == AnalysisSessionState.ANALYZING || state == AnalysisSessionState.PREPARING
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Análisis", style = MaterialTheme.typography.headlineLarge)
        Text("Solo tráfico de este celular, sin guardar contenido.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(session?.ssid ?: wifi?.ssid ?: "Nombre no disponible", style = MaterialTheme.typography.titleLarge)
                Text(if (session == null) "Listo para preparar" else formatState(session.state),
                    color = if (state == AnalysisSessionState.FAILED) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium)
                if (active) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state == AnalysisSessionState.ANALYZING || completed) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricValue("Duración", uiDuration(metrics.durationSeconds), Modifier.weight(1f))
                        MetricValue("Recibido", uiBytes(metrics.receivedBytes), Modifier.weight(1f))
                    }
                    DetailRow("Enviado", uiBytes(metrics.transmittedBytes))
                }
                if (completed && receipt != null) {
                    RiskBadge(receipt.riskLevel)
                    receipt.recommendations.firstOrNull()?.let { Text(it) }
                    Text("Evaluación orientativa; no confirma una amenaza.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (session != null && (state == AnalysisSessionState.ANALYZING || completed)) {
                    ExpandableSection("Detalles del análisis") {
                        DetailRow("Modo", if (session.captureMode == CaptureMode.FULL) "Captura IPv4" else "Prueba controlada")
                        DetailRow("Paquetes recibidos", metrics.receivedPackets.toString())
                        DetailRow("Paquetes enviados", metrics.transmittedPackets.toString())
                        if (session.captureMode == CaptureMode.CONTROLLED) {
                            DetailRow("Interpretados", metadata.parsedPackets.toString())
                            DetailRow("IPv4 / IPv6", "${metadata.ipv4Packets} / ${metadata.ipv6Packets}")
                            DetailRow("TCP / UDP", "${metadata.tcpPackets} / ${metadata.udpPackets}")
                            DetailRow("ICMP / Otros", "${metadata.icmpPackets} / ${metadata.otherTransportPackets}")
                            DetailRow("DNS / HTTP", "${metadata.dnsPackets} / ${metadata.httpPackets}")
                            DetailRow("TLS / QUIC", metadata.tlsOrQuicPackets.toString())
                            DetailRow("Destinos distintos", metadata.uniqueDestinations.toString())
                            if (metadata.unparsedPackets > 0) DetailRow("Sin interpretar", metadata.unparsedPackets.toString())
                            Text("Paquetes de prueba; no representan toda tu navegación.", style = MaterialTheme.typography.bodySmall)
                        } else if (!completed) {
                            Text("Las categorías del tráfico estarán en el historial al guardar.", style = MaterialTheme.typography.bodySmall)
                        }
                        receipt?.let { result ->
                            EventDetails(result.temporalCapture, result.detectedEvents)
                            DetailRow("Método", formatAssessmentVersion(result.assessmentVersion))
                            DetailRow("Muestra", formatSampleQuality(result.sampleQuality))
                            result.riskReasons.forEach { Text("• $it") }
                            result.recommendations.drop(1).forEach { Text("• $it") }
                        }
                    }
                }
            }
        }
        if (session == null || ready) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Conexión al servidor", style = MaterialTheme.typography.titleSmall)
                    Text(when {
                        checkingRelay -> "Comprobando…"
                        relayStatus?.available == true -> "Disponible"
                        relayStatus?.available == false -> "No disponible"
                        else -> "Sin comprobar"
                    }, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onCheckRelay, enabled = !checkingRelay) { Text("Comprobar") }
            }
            if (session == null) {
                Button(onClick = onPrepare, enabled = wifi != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Preparar análisis")
                }
            } else {
                Button(onClick = onStartFull,
                    enabled = relayStatus?.available == true && fullModeSupported && wifi != null && !checkingRelay,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Iniciar análisis") }
                if (!fullModeSupported) {
                    StatusNotice("La captura completa requiere Android 13 o superior.")
                } else if (relayStatus?.available != true) {
                    Text("Comprueba la conexión al servidor para comenzar.", style = MaterialTheme.typography.bodySmall)
                }
            }
            ExpandableSection("Opciones de prueba") {
                Text("Captura IPv4 experimental. IPv6 queda fuera del túnel.", style = MaterialTheme.typography.bodySmall)
                relayStatus?.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text("La prueba controlada usa paquetes conocidos para validar el funcionamiento.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onStartControlled, enabled = ready && wifi != null,
                    modifier = Modifier.fillMaxWidth()) { Text("Iniciar prueba controlada") }
            }
        }
        if (state == AnalysisSessionState.ANALYZING) {
            Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Finalizar análisis") }
        }
        if (com.anderson.wifiprevent.BuildConfig.DEBUG) {
            ExpandableSection("Laboratorio de pruebas") {
                LaboratoryPanel(state == AnalysisSessionState.ANALYZING && session?.captureMode == CaptureMode.FULL)
            }
        }
        if (uploading) LinearProgressIndicator(Modifier.fillMaxWidth())
        uploadMessage?.let { StatusNotice(it, error = uploadError) }
        if (completed) {
            if (uploadError) OutlinedButton(onClick = onRetryUpload, enabled = !uploading,
                modifier = Modifier.fillMaxWidth()) { Text("Reintentar guardado") }
            if (!uploading && !uploadError) {
                Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Ver en historial") }
            }
        }
        if (session != null && !ready) {
            TextButton(onClick = onPrepare,
                enabled = !active && !(completed && (uploading || uploadError)),
                modifier = Modifier.fillMaxWidth()) { Text("Nuevo análisis") }
        }
    }
}

private fun formatState(state: AnalysisSessionState): String = when (state) {
    AnalysisSessionState.PREPARING -> "Esperando autorización"
    AnalysisSessionState.READY -> "Listo para iniciar"
    AnalysisSessionState.ANALYZING -> "Análisis en curso"
    AnalysisSessionState.COMPLETED -> "Análisis finalizado"
    AnalysisSessionState.FAILED -> "No se pudo iniciar"
}
