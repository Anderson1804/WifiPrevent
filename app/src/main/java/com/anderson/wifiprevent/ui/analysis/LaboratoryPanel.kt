package com.anderson.wifiprevent.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.anderson.wifiprevent.data.lab.LabTrafficGenerator
import com.anderson.wifiprevent.data.vpn.developmentRelayHost
import com.anderson.wifiprevent.domain.traffic.LabProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LaboratoryPanel(activeFullCapture: Boolean) {
    var host by rememberSaveable { mutableStateOf("") }
    var profile by rememberSaveable { mutableStateOf(LabProfile.BURST) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Text("Laboratorio privado", style = MaterialTheme.typography.titleSmall)
    Text("Requiere el receptor de pruebas en otra IP privada. Genera tráfico sintético; no confirma amenazas.",
        style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(value = host, onValueChange = { host = it.trim() }, enabled = !busy,
        label = { Text("IPv4 del receptor") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LabProfile.entries.forEach { option ->
        FilterChip(selected = profile == option, onClick = { profile = option }, enabled = !busy,
            label = { Text(option.label) })
    }
    Button(onClick = {
        busy = true
        message = "Prueba en curso. Mantén esta pantalla abierta."
        scope.launch {
            try {
                val result = LabTrafficGenerator().run(host, profile, developmentRelayHost())
                message = "Código: ${result.executionId}. Recibidas: ${result.received}/${result.planned}. " +
                    "El registro del receptor permite verificar la ejecución."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "No se pudo ejecutar la prueba." }
            finally { busy = false }
        }
    }, enabled = activeFullCapture && !busy && host.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(if (busy) "Ejecutando…" else "Generar tráfico de prueba")
    }
    if (!activeFullCapture) Text("Inicia una captura IPv4 para habilitarlo.", style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
