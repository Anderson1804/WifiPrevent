package com.anderson.wifiprevent.ui.analysis

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.anderson.wifiprevent.domain.model.DetectedEvent
import com.anderson.wifiprevent.domain.model.TemporalCapture
import com.anderson.wifiprevent.ui.common.DetailRow

@Composable
fun EventDetails(capture: TemporalCapture?, events: List<DetectedEvent>) {
    if (capture == null) {
        Text("Sin mediciones temporales para esta sesión.", style = MaterialTheme.typography.bodySmall)
        return
    }
    Text("Patrones de conexión", style = MaterialTheme.typography.titleSmall)
    DetailRow("Observaciones", capture.observations.size.toString())
    DetailRow("Intentos fallidos", capture.observations.count { !it.success }.toString())
    if (capture.truncated) {
        Text("Serie incompleta. No se evaluaron patrones. Puedes exportar el JSON para revisarla.")
    } else if (events.isEmpty()) {
        Text("No se observaron los patrones definidos. Esto no garantiza seguridad.",
            style = MaterialTheme.typography.bodySmall)
    }
    events.forEach { event ->
        Text(event.description)
        DetailRow("Intervalo", "${event.startMs / 1000}–${event.endMs / 1000} s desde la preparación")
        DetailRow("Conexiones", event.count.toString())
        DetailRow("Método del evento", event.method)
        DetailRow("Riesgo del evento", when (event.riskLevel) {
            "low" -> "Bajo"; "medium" -> "Medio"; "high" -> "Alto"; else -> "Sin referencia suficiente"
        })
        Text("Patrón observado; no confirma un ataque.", style = MaterialTheme.typography.bodySmall)
    }
}
