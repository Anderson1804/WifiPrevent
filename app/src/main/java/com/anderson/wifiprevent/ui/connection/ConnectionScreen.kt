package com.anderson.wifiprevent.ui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.ui.common.formatSecurityType

@Composable
fun ConnectionScreen(
    wifi: WifiSnapshot?,
    permission: Boolean,
    location: Boolean,
    status: String,
    sending: Boolean,
    backendMessage: String?,
    backendError: Boolean,
    onPermission: () -> Unit,
    onSettings: () -> Unit,
    onLocation: () -> Unit,
    onWifi: () -> Unit,
    onCheck: () -> Unit,
    onAnalysis: () -> Unit,
    onHistory: () -> Unit,
    onAnalysisHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "WiFiPrevent",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Conoce tu conexión",
            style = MaterialTheme.typography.titleLarge
        )

        Text("Consulta tu conexión y guarda sus datos para revisarlos después.")

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(status, style = MaterialTheme.typography.labelLarge)

                Text(
                    text = wifi?.ssid
                        ?: if (wifi == null) {
                            "Conéctate a una red Wi-Fi"
                        } else {
                            "Nombre no disponible"
                        },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (wifi != null) {
                    ConnectionDetail(
                        label = "Señal",
                        value = wifi.rssi?.let { "$it dBm" }
                    )

                    ConnectionDetail(
                        label = "Frecuencia",
                        value = wifi.frequency?.let { "$it MHz" }
                    )

                    ConnectionDetail(
                        label = "Velocidad del enlace",
                        value = wifi.speed?.let { "$it Mbps" }
                    )

                    ConnectionDetail(
                        label = "Seguridad",
                        value = formatSecurityType(wifi.securityType)
                    )

                    ConnectionDetail(
                        label = "Acceso a Internet",
                        value = when {
                            wifi.captivePortal ->
                                "La red requiere iniciar sesión"

                            wifi.internetValidated ->
                                "Validado por Android"

                            else ->
                                "No validado por Android"
                        }
                    )

                    Text(
                        text = "La velocidad del enlace no es la velocidad de Internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (!permission) {
            Text(
                "Para mostrar el nombre de la red, Android requiere permiso " +
                        "de ubicación precisa. Esta versión no consulta coordenadas ni las guarda."
            )

            Button(
                onClick = onPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Permitir lectura de la red")
            }

            TextButton(onClick = onSettings) {
                Text("Abrir permisos de la aplicación")
            }
        }

        if (!location) {
            Text("Activa Ubicación en el teléfono si el nombre de la red no aparece.")

            OutlinedButton(onClick = onLocation) {
                Text("Abrir ajustes de ubicación")
            }
        }

        if (
            wifi != null &&
            wifi.ssid == null &&
            permission &&
            location
        ) {
            Text(
                "Android no proporcionó el nombre de la red. " +
                        "Los datos disponibles se muestran arriba."
            )
        }

        Button(
            onClick = onCheck,
            enabled = wifi != null && !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sending) "Enviando…" else "Guardar consulta")
        }

        OutlinedButton(
            onClick = onAnalysis,
            enabled = wifi != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analizar tráfico")
        }

        OutlinedButton(
            onClick = onWifi,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Abrir ajustes de Wi-Fi")
        }

        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver historial")
        }

        OutlinedButton(
            onClick = onAnalysisHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ver análisis guardados")
        }

        if (sending) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("Esperando la confirmación del servidor…")
        }

        backendMessage?.let { message ->
            Text(
                text = if (backendError) {
                    "Envío pendiente"
                } else {
                    "Confirmación del servidor"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (backendError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )

            Text(message)
        }

        HorizontalDivider()

        Text(
            text = "Al guardar, los datos mostrados y la evaluación de riesgo se " +
                    "conservan en la base de datos local de tu PC. Esta versión " +
                    "de desarrollo todavía no utiliza AWS.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ConnectionDetail(
    label: String,
    value: String?
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )

        Text(
            text = value ?: "No disponible",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
