package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.AnalysisSession
import com.anderson.wifiprevent.domain.model.AnalysisSessionState
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.ui.common.formatSecurityType

@Composable
fun AnalysisScreen(
    wifi: WifiSnapshot?,
    session: AnalysisSession?,
    metrics: TrafficMetrics,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Volver a la conexión")
        }

        Text(
            text = "Análisis de tráfico",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            "WiFiPrevent analizará únicamente metadatos del tráfico generado " +
                    "por este teléfono durante una sesión autorizada."
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = wifi?.ssid ?: "Sin conexión Wi-Fi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text("Seguridad: ${formatSecurityType(wifi?.securityType)}")
                Text("La sesión no leerá mensajes, contraseñas ni contenido de archivos.")
            }
        }

        Text(
            text = "Métricas de esta etapa",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("• Duración de la sesión")
        Text("• Cantidad de paquetes y bytes")
        Text(
            "Los contadores son agregados del teléfono durante el intervalo; " +
                    "todavía no identifican aplicaciones ni contenido."
        )
        Text(
            text = "Previsto para etapas posteriores",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("• Protocolos y puertos observados")
        Text("• Indicadores de conexiones sin cifrar")

        if (session == null) {
            Button(
                onClick = onPrepare,
                enabled = wifi != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preparar sesión")
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatState(session.state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Red: ${session.ssid ?: "Nombre no disponible"}")
                    Text(
                        text = "Sesión: ${session.id}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    when (session.state) {
                        AnalysisSessionState.READY -> Text(
                            "La sesión está lista para solicitar la autorización VPN de Android."
                        )
                        AnalysisSessionState.PREPARING -> Text(
                            "Esperando la autorización VPN del sistema."
                        )
                        AnalysisSessionState.ANALYZING -> {
                            MetricsContent(metrics, final = false)
                        }
                        AnalysisSessionState.COMPLETED -> {
                            MetricsContent(metrics, final = true)
                            Text("La sesión terminó sin almacenar contenido de tráfico.")
                        }
                        AnalysisSessionState.FAILED -> Text(
                            "Android no autorizó la sesión. Puedes preparar una nueva."
                        )
                    }
                }
            }

            when (session.state) {
                AnalysisSessionState.READY -> Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Autorizar e iniciar")
                }
                AnalysisSessionState.ANALYZING -> Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Detener sesión")
                }
                else -> Unit
            }

            OutlinedButton(
                onClick = onPrepare,
                enabled = session.state != AnalysisSessionState.ANALYZING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preparar una sesión nueva")
            }
        }
    }
}

@Composable
private fun MetricsContent(metrics: TrafficMetrics, final: Boolean) {
    Text(
        "${if (final) "Duración final" else "Duración"}: " +
                formatDuration(metrics.durationSeconds)
    )
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

private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun formatState(state: AnalysisSessionState): String = when (state) {
    AnalysisSessionState.PREPARING -> "Preparando sesión"
    AnalysisSessionState.READY -> "Sesión preparada"
    AnalysisSessionState.ANALYZING -> "Análisis en curso"
    AnalysisSessionState.COMPLETED -> "Análisis completado"
    AnalysisSessionState.FAILED -> "El análisis no pudo completarse"
}
