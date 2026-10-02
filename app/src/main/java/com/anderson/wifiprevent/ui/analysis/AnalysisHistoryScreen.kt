package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.AnalysisHistorySummary
import com.anderson.wifiprevent.ui.common.*

@Composable
fun AnalysisHistoryScreen(
    entries: List<AnalysisHistoryEntry>, summary: AnalysisHistorySummary?,
    riskFilter: String?, captureModeFilter: String?, deletingId: String?,
    loading: Boolean, error: String?, hasMore: Boolean,
    onRefresh: () -> Unit, onMore: () -> Unit,
    onRiskFilterChange: (String?) -> Unit, onCaptureModeFilterChange: (String?) -> Unit,
    onDelete: (String) -> Unit, onShare: (AnalysisHistoryEntry) -> Unit,
    exportingId: String?, exportMessage: String?, onExport: (AnalysisHistoryEntry) -> Unit,
    onExportTemporal: (AnalysisHistoryEntry) -> Unit = {},
    onExportExperiment: (AnalysisHistoryEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<AnalysisHistoryEntry?>(null) }
    var pendingExport by remember { mutableStateOf<AnalysisHistoryEntry?>(null) }
    var experimentExport by remember { mutableStateOf(false) }
    var temporalExport by remember { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    pendingDelete?.let { entry ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Eliminar análisis") },
            text = { Text("Se eliminará este registro. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(entry.id) },
                enabled = deletingId == null) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } })
    }
    pendingExport?.let { entry ->
        AlertDialog(onDismissRequest = { pendingExport = null }, title = { Text("Exportar métricas") },
            text = { Text("Metadatos sin nombre de red ni identificadores de instalación. Las predicciones no son referencias de investigación. Elige una carpeta local para guardar el archivo.") },
            confirmButton = { TextButton(onClick = { pendingExport = null; if (experimentExport) onExportExperiment(entry) else if (temporalExport) onExportTemporal(entry) else onExport(entry) }) { Text("Guardar archivo") } },
            dismissButton = { TextButton(onClick = { pendingExport = null }) { Text("Cancelar") } })
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        summary?.let { value -> item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${value.totalSessions} análisis guardados", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricValue("Riesgo bajo", value.lowRisk.toString(), Modifier.weight(1f))
                        MetricValue("Medio", value.mediumRisk.toString(), Modifier.weight(1f))
                        MetricValue("Alto", value.highRisk.toString(), Modifier.weight(1f))
                    }
                    if (value.unknownRisk + value.notEvaluated > 0) {
                        Text("Sin evaluación suficiente: ${value.unknownRisk + value.notEvaluated}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showFilters = !showFilters }) {
                    Text(if (showFilters) "Ocultar filtros" else if (riskFilter != null || captureModeFilter != null) "Filtros activos" else "Filtros")
                }
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Actualizar") }
            }
            if (showFilters) {
                FilterRow("Riesgo", listOf(null to "Todos", "low" to "Bajo", "medium" to "Medio", "high" to "Alto", "unknown" to "Sin información"),
                    riskFilter, !loading, onRiskFilterChange)
                FilterRow("Captura", listOf(null to "Todas", "full" to "Completa", "controlled" to "De prueba"),
                    captureModeFilter, !loading, onCaptureModeFilterChange)
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { StatusNotice(it, error = true) } }
        exportMessage?.let { item { StatusNotice(it) } }
        if (!loading && error == null && entries.isEmpty()) item {
            Text(if (riskFilter != null || captureModeFilter != null) "No hay resultados con estos filtros" else "Aún no hay análisis",
                style = MaterialTheme.typography.titleMedium)
            Text(if (riskFilter != null || captureModeFilter != null) "Prueba otro nivel o tipo de captura." else "Realiza un análisis para guardar el primero.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries, key = { it.id }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.ssid ?: "Nombre no disponible", style = MaterialTheme.typography.titleLarge)
                    Text(uiDate(entry.receivedAt), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    RiskBadge(entry.riskLevel, entry.assessmentScope != null)
                    Text("${uiDuration(entry.metrics.durationSeconds)} · ${uiBytes(entry.metrics.receivedBytes + entry.metrics.transmittedBytes)} · " +
                        if (entry.captureMode == "full") "Captura completa" else "Prueba controlada",
                        style = MaterialTheme.typography.bodyMedium)
                    ExpandableSection("Detalles y acciones") {
                        if (entry.riskReasons.isNotEmpty()) {
                            Text("Motivos", style = MaterialTheme.typography.titleSmall)
                            entry.riskReasons.forEach { Text("• $it") }
                        }
                        if (entry.recommendations.isNotEmpty()) {
                            Text("Qué puedes hacer", style = MaterialTheme.typography.titleSmall)
                            entry.recommendations.forEach { Text("• $it") }
                        }
                        EventDetails(entry.temporalCapture, entry.detectedEvents)
                        DetailRow("Seguridad", formatSecurityType(entry.securityType))
                        DetailRow("Muestra", formatSampleQuality(entry.sampleQuality))
                        DetailRow("Método", formatAssessmentVersion(entry.assessmentVersion))
                        DetailRow("Portal de acceso", when (entry.captivePortal) { true -> "Detectado"; false -> "No detectado"; null -> "Sin dato" })
                        DetailRow("Recibido", uiBytes(entry.metrics.receivedBytes))
                        DetailRow("Enviado", uiBytes(entry.metrics.transmittedBytes))
                        DetailRow("Paquetes recibidos", entry.metrics.receivedPackets.toString())
                        DetailRow("Paquetes enviados", entry.metrics.transmittedPackets.toString())
                        if (entry.relayMetricsCollected) {
                            Text("Categorías por puerto", style = MaterialTheme.typography.titleSmall)
                            DetailRow("Conexiones TCP", entry.relayMetrics.tcpConnections.toString())
                            DetailRow("Datagramas UDP", entry.relayMetrics.udpDatagrams.toString())
                            DetailRow("DNS / HTTP", "${entry.relayMetrics.dnsObservations} / ${entry.relayMetrics.httpObservations}")
                            DetailRow("TLS / QUIC", entry.relayMetrics.tlsOrQuicObservations.toString())
                            DetailRow("Otras observaciones", entry.relayMetrics.otherObservations.toString())
                            DetailRow("Destinos distintos", entry.relayMetrics.uniqueDestinations.toString())
                        } else if (entry.captureMode == "full") {
                            Text("Categorías de tráfico no disponibles para esta sesión.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            DetailRow("Paquetes interpretados", entry.metadata.parsedPackets.toString())
                            DetailRow("IPv4 / IPv6", "${entry.metadata.ipv4Packets} / ${entry.metadata.ipv6Packets}")
                            DetailRow("TCP / UDP", "${entry.metadata.tcpPackets} / ${entry.metadata.udpPackets}")
                            DetailRow("ICMP / Otros", "${entry.metadata.icmpPackets} / ${entry.metadata.otherTransportPackets}")
                            DetailRow("DNS / HTTP", "${entry.metadata.dnsPackets} / ${entry.metadata.httpPackets}")
                            DetailRow("TLS / QUIC", entry.metadata.tlsOrQuicPackets.toString())
                            DetailRow("Destinos distintos", entry.metadata.uniqueDestinations.toString())
                            if (entry.metadata.unparsedPackets > 0) DetailRow("Sin interpretar", entry.metadata.unparsedPackets.toString())
                        }
                        entry.indicators.forEach { observation ->
                            Text(observation.title, style = MaterialTheme.typography.titleSmall)
                            Text(observation.description)
                        }
                        OutlinedButton(onClick = { onShare(entry) }, modifier = Modifier.fillMaxWidth()) { Text("Compartir resumen") }
                        if (entry.captureMode == "full") {
                            val eligible = entry.relayMetricsCollected && entry.metrics.durationSeconds >= 30 &&
                                entry.metrics.receivedPackets + entry.metrics.transmittedPackets >= 100
                            OutlinedButton(onClick = { experimentExport = false; temporalExport = false; pendingExport = entry },
                                enabled = eligible && exportingId == null && deletingId == null && !loading,
                                modifier = Modifier.fillMaxWidth()) {
                                Text(if (exportingId == entry.id) "Preparando CSV…" else "Exportar métricas CSV")
                            }
                            if (!eligible) Text("Exportación: mínimo 30 s, 100 paquetes y categorías disponibles.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.temporalCapture != null) {
                            OutlinedButton(onClick = { experimentExport = true; pendingExport = entry },
                                enabled = exportingId == null && deletingId == null && !loading,
                                modifier = Modifier.fillMaxWidth()) { Text("Exportar registro experimental JSON") }

                            OutlinedButton(onClick = { experimentExport = false; temporalExport = true; pendingExport = entry },
                                enabled = !entry.temporalCapture.truncated && entry.temporalCapture.elapsedMs >= 30_000 &&
                                    exportingId == null && deletingId == null && !loading,
                                modifier = Modifier.fillMaxWidth()) { Text("Exportar observaciones temporales") }
                        }
                        TextButton(onClick = { pendingDelete = entry }, enabled = deletingId == null && exportingId == null && !loading,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Text(if (deletingId == entry.id) "Eliminando…" else "Eliminar análisis")
                        }
                    }
                }
            }
        }
        if (hasMore) item {
            OutlinedButton(onClick = onMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
        }
        if (entries.isNotEmpty()) item {
            Text("Evaluación orientativa; no confirma una amenaza.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterRow(label: String, choices: List<Pair<String?, String>>, selected: String?, enabled: Boolean, onChange: (String?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { onChange(value) }, enabled = enabled, label = { Text(text) })
            }
        }
    }
}
