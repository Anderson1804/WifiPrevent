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
import com.anderson.wifiprevent.ui.common.formatSecurityType

@Composable
fun AnalysisScreen(
    wifi: WifiSnapshot?,
    session: AnalysisSession?,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
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
            text = "Datos previstos para la siguiente etapa",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text("• Duración de la sesión")
        Text("• Cantidad de paquetes y bytes")
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
                    Text(
                        "La autorización VPN y la recolección de métricas se " +
                                "incorporarán en el siguiente paso."
                    )
                }
            }

            OutlinedButton(
                onClick = onPrepare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preparar una sesión nueva")
            }
        }
    }
}

private fun formatState(state: AnalysisSessionState): String = when (state) {
    AnalysisSessionState.PREPARING -> "Preparando sesión"
    AnalysisSessionState.READY -> "Sesión preparada"
    AnalysisSessionState.ANALYZING -> "Análisis en curso"
    AnalysisSessionState.COMPLETED -> "Análisis completado"
    AnalysisSessionState.FAILED -> "El análisis no pudo completarse"
}
