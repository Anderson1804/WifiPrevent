package com.anderson.wifiprevent.ui.connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.domain.model.ConnectionReceipt
import com.anderson.wifiprevent.ui.common.*

@Composable
fun ConnectionScreen(
    wifi: WifiSnapshot?, permission: Boolean, location: Boolean, status: String,
    sending: Boolean, backendMessage: String?, backendError: Boolean,
    analysisActive: Boolean,
    receipt: ConnectionReceipt?,
    onPermission: () -> Unit, onSettings: () -> Unit, onLocation: () -> Unit,
    onWifi: () -> Unit, onCheck: () -> Unit, onAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WiFiPrevent", style = MaterialTheme.typography.headlineLarge)
            Text("Tu conexión, de un vistazo", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(AppIcons.Connection, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Text(wifi?.ssid ?: if (wifi == null) "Sin conexión Wi-Fi" else "Nombre no disponible",
                    style = MaterialTheme.typography.headlineMedium)
                if (wifi != null) {
                    Text(formatSecurityType(wifi.securityType), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small) {
                        Text(when {
                            wifi.captivePortal -> "Requiere iniciar sesión"
                            wifi.internetValidated -> "Internet disponible"
                            else -> "Internet sin confirmar"
                        }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                    ExpandableSection("Datos de conexión") {
                        DetailRow("Señal", wifi.rssi?.let { "$it dBm" } ?: "No disponible")
                        DetailRow("Frecuencia", wifi.frequency?.let { "$it MHz" } ?: "No disponible")
                        DetailRow("Velocidad del enlace", wifi.speed?.let { "$it Mbps" } ?: "No disponible")
                        Text("La velocidad del enlace no mide la velocidad de Internet.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!permission) {
            StatusNotice("Permite leer el nombre de la red. Android pide ubicación; no guardamos coordenadas.")
            Button(onClick = onPermission, modifier = Modifier.fillMaxWidth()) { Text("Permitir lectura de Wi-Fi") }
            TextButton(onClick = onSettings) { Text("Abrir permisos") }
        } else if (!location && wifi?.ssid == null) {
            TextButton(onClick = onLocation) { Text("Activar ubicación para ver la red") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAnalysis, enabled = wifi != null || analysisActive,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (analysisActive) "Continuar análisis" else "Analizar conexión")
            }
            OutlinedButton(onClick = onCheck, enabled = wifi != null && !sending,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (sending) "Guardando…" else "Guardar consulta rápida")
            }
            TextButton(onClick = onWifi, modifier = Modifier.fillMaxWidth()) { Text("Cambiar red Wi-Fi") }
        }
        if (sending) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        backendMessage?.let {
            StatusNotice(it, error = backendError)
            if (!backendError && receipt != null) {
                RiskBadge(receipt.riskLevel, receipt.analysisPerformed)
                ExpandableSection("Motivos de la evaluación") {
                    receipt.riskReasons.forEach { reason -> Text("• $reason") }
                    Text("Evaluación orientativa; no confirma una amenaza.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Solo analiza la conexión y el tráfico de este celular.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
