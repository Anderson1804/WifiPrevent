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
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.data.vpn.SocksRelayStatus
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.ui.common.formatSecurityType

@Composable
fun AnalysisScreen(
    wifi: WifiSnapshot?,
    session: AnalysisSession?,
    metrics: TrafficMetrics,
    metadata: TrafficMetadataSummary,
    uploadMessage: String?,
    uploadError: Boolean,
    uploading: Boolean,
    relayStatus: SocksRelayStatus?,
    checkingRelay: Boolean,
    fullModeSupported: Boolean,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onStartControlled: () -> Unit,
    onStartFull: () -> Unit,
    onStop: () -> Unit,
    onRetryUpload: () -> Unit,
    onCheckRelay: () -> Unit,
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
        Text("La validación de protocolos usa una ruta VPN aislada y tráfico de prueba controlado.")
        Text(
            "Modo actual: validación controlada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "La captura completa experimental reenvía IPv4 mediante el relé local. " +
                    "IPv6 permanece fuera del túnel durante esta etapa."
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Transporte de desarrollo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    relayStatus?.message
                        ?: "Comprueba el relé SOCKS5 y el motor nativo del túnel.",
                    color = if (relayStatus?.available == false) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    "Esta comprobación valida el enlace con la PC; todavía no activa la captura completa.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = onCheckRelay,
                    enabled = !checkingRelay,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (checkingRelay) "Comprobando…" else "Comprobar transporte local")
                }
            }
        }

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
                            MetadataContent(metadata, session.captureMode)
                        }
                        AnalysisSessionState.COMPLETED -> {
                            MetricsContent(metrics, final = true)
                            MetadataContent(metadata, session.captureMode)
                            Text("La sesión terminó sin almacenar contenido de tráfico.")
                            uploadMessage?.let {
                                Text(
                                    it,
                                    color = if (uploadError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        AnalysisSessionState.FAILED -> Text(
                            "Android no autorizó la sesión. Puedes preparar una nueva."
                        )
                    }
                }
            }

            when (session.state) {
                AnalysisSessionState.READY -> {
                    Button(
                        onClick = onStartControlled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar validación controlada")
                    }
                    Button(
                        onClick = onStartFull,
                        enabled = relayStatus?.available == true && fullModeSupported,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar captura completa experimental")
                    }
                    if (!fullModeSupported) {
                        Text(
                            "El modo completo requiere Android 13 o superior.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (relayStatus?.available != true) {
                        Text(
                            "Comprueba primero el transporte local.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                enabled = session.state != AnalysisSessionState.ANALYZING &&
                        !(session.state == AnalysisSessionState.COMPLETED &&
                                (uploading || uploadError)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preparar una sesión nueva")
            }
            if (session.state == AnalysisSessionState.COMPLETED && uploadError) {
                OutlinedButton(
                    onClick = onRetryUpload,
                    enabled = !uploading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uploading) "Enviando…" else "Reintentar guardado")
                }
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

@Composable
private fun MetadataContent(metadata: TrafficMetadataSummary, captureMode: CaptureMode) {
    Text(
        "Metadatos interpretados",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text("Paquetes válidos: ${metadata.parsedPackets}")
    Text("IPv4: ${metadata.ipv4Packets} · IPv6: ${metadata.ipv6Packets}")
    Text("TCP: ${metadata.tcpPackets} · UDP: ${metadata.udpPackets}")
    Text("ICMP: ${metadata.icmpPackets} · Otros: ${metadata.otherTransportPackets}")
    Text("DNS: ${metadata.dnsPackets}")
    Text("HTTP: ${metadata.httpPackets}")
    Text("TLS/QUIC: ${metadata.tlsOrQuicPackets}")
    Text("Destinos únicos: ${metadata.uniqueDestinations}")
    if (metadata.unparsedPackets > 0) {
        Text("Paquetes no interpretados: ${metadata.unparsedPackets}")
    }
    Text(
        if (captureMode == CaptureMode.CONTROLLED) {
            "Estos valores provienen de paquetes controlados de validación."
        } else {
            "El motor nativo reenvía el tráfico completo; la clasificación detallada de " +
                    "protocolos todavía está en desarrollo."
        },
        style = MaterialTheme.typography.bodySmall
    )
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
