package com.anderson.wifiprevent.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.anderson.wifiprevent.domain.model.HistoryEntry
@Composable
fun HistoryScreen(entries: List<HistoryEntry>, loading: Boolean, error: String?,
                  hasMore: Boolean, onBack: () -> Unit, onRefresh: () -> Unit,
                  onMore: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Volver a la conexión") }
            Text("Historial", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold)
            Text("Consultas guardadas desde esta instalación. El riesgo todavía no se evalúa.")
            OutlinedButton(onClick = onRefresh, enabled = !loading,
                modifier = Modifier.fillMaxWidth()) { Text("Actualizar historial") }
        }
        if (loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
        } }
        if (!loading && error == null && entries.isEmpty()) item {
            Text("Todavía no hay consultas guardadas.", style = MaterialTheme.typography.titleMedium)
            Text("Vuelve a la conexión y pulsa Guardar consulta para crear el primer registro.")
        }
        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(entry.ssid ?: "Nombre no disponible", style = MaterialTheme.typography.titleLarge)
                    Text(formatHistoryDate(entry.receivedAt), style = MaterialTheme.typography.labelLarge)
                    Text("Riesgo no evaluado", fontWeight = FontWeight.Bold)
                    Text("Señal: ${entry.rssi?.let { "$it dBm" } ?: "No disponible"}")
                    Text("Frecuencia: ${entry.frequency?.let { "$it MHz" } ?: "No disponible"}")
                    Text("Velocidad del enlace: ${entry.speed?.let { "$it Mbps" } ?: "No disponible"}")
                    Text(when {
                        entry.captivePortal -> "La red requería iniciar sesión"
                        entry.internetValidated -> "Internet validado por Android al consultar"
                        else -> "Internet no validado por Android al consultar"
                    })
                    Text("Recibo: ${entry.id}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (hasMore) item {
            OutlinedButton(onClick = onMore, enabled = !loading,
                modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
        }
    }
}

private fun formatHistoryDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.forLanguageTag("es")))
}.getOrDefault(value)
