package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.ui.common.formatSecurityType
import com.anderson.wifiprevent.ui.common.formatRiskLevel
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalysisHistoryScreen(
    entries: List<AnalysisHistoryEntry>,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
        } }
        if (!loading && error == null && entries.isEmpty()) item {
            Text("Todavía no hay análisis guardados.", style = MaterialTheme.typography.titleMedium)
            Text("Realiza y detén una sesión para crear el primer registro.")
        }
        items(entries, key = { it.id }) { entry -> AnalysisHistoryCard(entry) }
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
private fun AnalysisHistoryCard(entry: AnalysisHistoryEntry) {
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
                "Evaluación preliminar: ${formatRiskLevel(entry.riskLevel, entry.assessmentScope != null)}",
                fontWeight = FontWeight.Bold
            )
            entry.riskReasons.forEach { reason -> Text("• $reason") }
            if (!entry.trafficAnalysisPerformed) {
                Text(
                    "Alcance actual: metadatos de conexión y volumen. La clasificación " +
                            "por protocolos y destinos todavía no está implementada.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            AnalysisMetrics(entry.metrics)
            Text("Sesión: ${entry.id}", style = MaterialTheme.typography.bodySmall)
        }
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
