package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.AnalysisHistorySummary
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.ui.common.formatSecurityType
import com.anderson.wifiprevent.ui.common.formatRiskLevel
import com.anderson.wifiprevent.ui.common.formatAssessmentVersion
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalysisHistoryScreen(
    entries: List<AnalysisHistoryEntry>,
    summary: AnalysisHistorySummary?,
    riskFilter: String?,
    captureModeFilter: String?,
    deletingId: String?,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    onRiskFilterChange: (String?) -> Unit,
    onCaptureModeFilterChange: (String?) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<AnalysisHistoryEntry?>(null) }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (deletingId == null) pendingDelete = null },
            title = { Text("Eliminar sesión") },
            text = {
                Text(
                    "Se eliminará permanentemente esta sesión del servidor local. " +
                            "Esta acción no puede deshacerse."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = deletingId == null,
                    onClick = {
                        onDelete(entry.id)
                        pendingDelete = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(
                    enabled = deletingId == null,
                    onClick = { pendingDelete = null }
                ) { Text("Cancelar") }
            }
        )
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("Volver a la conexión") }
            Text(
                "Historial de análisis",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text("Sesiones terminadas y guardadas por esta instalación.")
            OutlinedButton(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Actualizar análisis") }
        }
        summary?.let { value -> item { AnalysisSummaryCard(value) } }
        item {
            HistoryFilters(
                riskFilter = riskFilter,
                captureModeFilter = captureModeFilter,
                enabled = !loading,
                onRiskFilterChange = onRiskFilterChange,
                onCaptureModeFilterChange = onCaptureModeFilterChange
            )
        }
        if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
        } }
        if (!loading && error == null && entries.isEmpty()) item {
            Text("Todavía no hay análisis guardados.", style = MaterialTheme.typography.titleMedium)
            Text("Realiza y detén una sesión para crear el primer registro.")
        }
        items(entries, key = { it.id }) { entry ->
            AnalysisHistoryCard(
                entry = entry,
                deleting = deletingId == entry.id,
                deleteEnabled = deletingId == null && !loading,
                onDelete = { pendingDelete = entry }
            )
        }
        if (hasMore) item {
            OutlinedButton(
                onClick = onMore,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cargar más") }
        }
    }
}

@Composable
private fun HistoryFilters(
    riskFilter: String?,
    captureModeFilter: String?,
    enabled: Boolean,
    onRiskFilterChange: (String?) -> Unit,
    onCaptureModeFilterChange: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filtrar por riesgo", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                null to "Todos",
                "low" to "Bajo",
                "medium" to "Medio",
                "high" to "Alto",
                "unknown" to "Sin información"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = riskFilter == value,
                    enabled = enabled,
                    onClick = { onRiskFilterChange(value) },
                    label = { Text(label) }
                )
            }
        }
        Text("Filtrar por modo", fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                null to "Todos",
                "controlled" to "Controlada",
                "full" to "Completa"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = captureModeFilter == value,
                    enabled = enabled,
                    onClick = { onCaptureModeFilterChange(value) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun AnalysisSummaryCard(summary: AnalysisHistorySummary) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Resumen de esta instalación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Sesiones registradas: ${summary.totalSessions}")
            Text(
                "Riesgo bajo: ${summary.lowRisk} · medio: ${summary.mediumRisk} · " +
                        "alto: ${summary.highRisk}"
            )
            if (summary.unknownRisk > 0 || summary.notEvaluated > 0) {
                Text(
                    "Sin información suficiente: ${summary.unknownRisk} · " +
                            "sin evaluación histórica: ${summary.notEvaluated}"
                )
            }
            Text(
                "Controladas: ${summary.controlledSessions} · " +
                        "completas: ${summary.fullSessions}"
            )
        }
    }
}

@Composable
private fun AnalysisHistoryCard(
    entry: AnalysisHistoryEntry,
    deleting: Boolean,
    deleteEnabled: Boolean,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                entry.ssid ?: "Nombre no disponible",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(formatAnalysisDate(entry.receivedAt), style = MaterialTheme.typography.labelLarge)
            Text("Seguridad: ${formatSecurityType(entry.securityType)}")
            Text(
                "Modo: " + if (entry.captureMode == "full") {
                    "captura completa experimental"
                } else {
                    "validación controlada"
                }
            )
            Text(
                "Evaluación preliminar: ${formatRiskLevel(entry.riskLevel, entry.assessmentScope != null)}",
                fontWeight = FontWeight.Bold
            )
            Text("Método: ${formatAssessmentVersion(entry.assessmentVersion)}")
            entry.riskReasons.forEach { reason -> Text("• $reason") }
            if (entry.indicators.isNotEmpty()) {
                Text("Observaciones técnicas", fontWeight = FontWeight.Bold)
                entry.indicators.forEach { indicator ->
                    Text("${indicatorLabel(indicator.severity)} ${indicator.title}")
                    Text(indicator.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                if (entry.captureMode == "full" && entry.trafficAnalysisPerformed) {
                    "Alcance actual: seguridad de la red y comportamiento agregado del túnel IPv4. " +
                            "La clasificación detallada de protocolos está pendiente."
                } else {
                    "Alcance actual: metadatos de conexión y una muestra controlada de " +
                            "protocolos y destinos. Todavía no representa todo el tráfico."
                },
                style = MaterialTheme.typography.bodySmall
            )
            AnalysisMetrics(entry.metrics)
            if (entry.captureMode == "controlled") StoredMetadata(entry.metadata)
            Text("Sesión: ${entry.id}", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onDelete,
                enabled = deleteEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (deleting) "Eliminando…" else "Eliminar sesión")
            }
        }
    }
}

@Composable
private fun StoredMetadata(metadata: TrafficMetadataSummary) {
    Text("Metadatos controlados", fontWeight = FontWeight.Bold)
    Text("Interpretados: ${metadata.parsedPackets}")
    Text("IPv4: ${metadata.ipv4Packets} · IPv6: ${metadata.ipv6Packets}")
    Text("TCP: ${metadata.tcpPackets} · UDP: ${metadata.udpPackets}")
    Text("ICMP: ${metadata.icmpPackets} · Otros: ${metadata.otherTransportPackets}")
    Text("DNS: ${metadata.dnsPackets} · HTTP: ${metadata.httpPackets}")
    Text("TLS/QUIC: ${metadata.tlsOrQuicPackets}")
    Text("Destinos únicos: ${metadata.uniqueDestinations}")
    if (metadata.unparsedPackets > 0) {
        Text("No interpretados: ${metadata.unparsedPackets}")
    }
}

@Composable
private fun AnalysisMetrics(metrics: TrafficMetrics) {
    Text("Duración: ${formatDuration(metrics.durationSeconds)}")
    Text("Recibido: ${formatBytes(metrics.receivedBytes)}")
    Text("Enviado: ${formatBytes(metrics.transmittedBytes)}")
    Text("Paquetes recibidos: ${metrics.receivedPackets}")
    Text("Paquetes enviados: ${metrics.transmittedPackets}")
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}

private fun formatDuration(totalSeconds: Long): String =
    "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatAnalysisDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("es")))
}.getOrDefault(value)

private fun indicatorLabel(severity: String): String = when (severity) {
    "warning" -> "Advertencia:"
    "medium" -> "Atención:"
    else -> "Información:"
}
