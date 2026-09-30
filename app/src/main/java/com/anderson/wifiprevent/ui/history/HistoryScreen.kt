package com.anderson.wifiprevent.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.ui.common.*

enum class HistoryCategory { ANALYSES, CONNECTIONS }

@Composable
fun HistoryScreen(
    category: HistoryCategory,
    onCategoryChange: (HistoryCategory) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Historial", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == HistoryCategory.ANALYSES,
                onClick = { onCategoryChange(HistoryCategory.ANALYSES) }, label = { Text("Análisis") })
            FilterChip(selected = category == HistoryCategory.CONNECTIONS,
                onClick = { onCategoryChange(HistoryCategory.CONNECTIONS) }, label = { Text("Consultas rápidas") })
        }
        content()
    }
}

@Composable
fun ConnectionHistoryList(
    entries: List<HistoryEntry>, loading: Boolean, error: String?, hasMore: Boolean,
    onRefresh: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRefresh, enabled = !loading) { Text("Actualizar") }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { StatusNotice(it, error = true) } }
        if (!loading && error == null && entries.isEmpty()) item {
            Text("Aún no hay consultas", style = MaterialTheme.typography.titleMedium)
            Text("Guarda una consulta rápida desde Inicio.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(entries, key = { it.id }) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.ssid ?: "Nombre no disponible", style = MaterialTheme.typography.titleLarge)
                    Text(uiDate(entry.receivedAt), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RiskBadge(entry.riskLevel, entry.analysisPerformed)
                    Text(formatSecurityType(entry.securityType), style = MaterialTheme.typography.bodyMedium)
                    ExpandableSection("Ver consulta") {
                        if (entry.riskReasons.isNotEmpty()) {
                            Text("Motivos", style = MaterialTheme.typography.titleSmall)
                            entry.riskReasons.forEach { Text("• $it") }
                        }
                        DetailRow("Señal", entry.rssi?.let { "$it dBm" } ?: "No disponible")
                        DetailRow("Frecuencia", entry.frequency?.let { "$it MHz" } ?: "No disponible")
                        DetailRow("Enlace", entry.speed?.let { "$it Mbps" } ?: "No disponible")
                        DetailRow("Internet", when {
                            entry.captivePortal -> "Requería iniciar sesión"
                            entry.internetValidated -> "Disponible al consultar"
                            else -> "Sin confirmar"
                        })
                    }
                }
            }
        }
        if (hasMore) item {
            OutlinedButton(onClick = onMore, enabled = !loading,
                modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
        }
        if (entries.isNotEmpty()) item {
            Text("Evaluación orientativa; no confirma una amenaza.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
