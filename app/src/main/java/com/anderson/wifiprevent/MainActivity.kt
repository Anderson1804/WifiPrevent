package com.anderson.wifiprevent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.anderson.wifiprevent.data.network.WifiConnectionObserver
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.ui.connection.ConnectionScreen
import com.anderson.wifiprevent.ui.history.HistoryScreen
import com.anderson.wifiprevent.ui.common.formatRiskLevel
import com.anderson.wifiprevent.ui.theme.WifiPreventTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.anderson.wifiprevent.data.remote.BackendClient
import com.anderson.wifiprevent.data.repository.ConnectionRepository

class MainActivity : ComponentActivity() {
    private var showHistory by mutableStateOf(false)
    private var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
    private var historyLoading by mutableStateOf(false)
    private var historyError by mutableStateOf<String?>(null)
    private var historyCursor: String? = null
    private var historyHasMore by mutableStateOf(false)
    private var connection by mutableStateOf<WifiSnapshot?>(null)
    private var permissionGranted by mutableStateOf(false)
    private var locationEnabled by mutableStateOf(false)
    private var status by mutableStateOf("Buscando una conexión Wi-Fi…")
    private var sending by mutableStateOf(false)
    private var backendMessage by mutableStateOf<String?>(null)
    private var backendError by mutableStateOf(false)
    private var submission: Job? = null

    private val connectionRepository by lazy {
        ConnectionRepository(
            backendClient = BackendClient(applicationContext)
        )
    }

    private val wifiConnectionObserver by lazy {
        WifiConnectionObserver(applicationContext) { newConnection, newStatus ->
            if (connection?.ssid != newConnection?.ssid) {
                backendMessage = null
            }

            connection = newConnection
            status = newStatus
        }
    }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (showHistory) historyError = if (granted) "Permiso concedido. Pulsa Actualizar historial."
            else "Permiso de red local requerido. Habilítalo desde los permisos de la aplicación."
        backendError = !granted
        backendMessage = if (granted) "Permiso concedido. Pulsa Guardar consulta."
            else "El envío requiere permiso de red local. Puedes habilitarlo en los permisos de la aplicación."
    }
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { restartObservation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WifiPreventTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    BackHandler(enabled = showHistory) { showHistory = false }
                    if (showHistory) {
                        HistoryScreen(
                            historyEntries,
                            historyLoading,
                            historyError,
                            historyHasMore,
                            onBack = { showHistory = false },
                            onRefresh = { loadHistory() },
                            onMore = { loadHistory(more = true) },
                            modifier = Modifier.padding(padding)
                        )
                    } else ConnectionScreen(
                        connection, permissionGranted, locationEnabled, status, sending, backendMessage, backendError,
                        onPermission = { permissionRequest.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )) },
                        onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName"))) },
                        onLocation = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                        onWifi = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                        onCheck = { sendConnection() },
                        onHistory = { showHistory = true; loadHistory() },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restartObservation()
    }

    override fun onPause() {
        stopObservation()
        super.onPause()
    }

    private fun loadHistory(more: Boolean = false) {
        if (historyLoading) return
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this,
                "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            return
        }
        val cursor = if (more) historyCursor else null
        if (more && cursor == null) return
        historyLoading = true
        historyError = null
        lifecycleScope.launch {
            try {
                val page =
                    connectionRepository.getHistory(cursor)
                historyEntries = if (more) (historyEntries + page.entries).distinctBy { it.id } else page.entries
                historyCursor = page.nextBefore
                historyHasMore = page.nextBefore != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                historyError = e.message ?: "No se pudo consultar el historial."
            } finally {
                historyLoading = false
            }
        }
    }

    private fun sendConnection() {
        val snapshot = connection ?: return
        if (sending) return
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(this,
                "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            return
        }
        sending = true
        backendMessage = null
        backendError = false
        submission = lifecycleScope.launch {
            try {
                val receipt =
                    connectionRepository.saveConnection(snapshot)
                val risk = formatRiskLevel(
                    receipt.riskLevel,
                    receipt.analysisPerformed
                )
                val reasons = receipt.riskReasons.joinToString(separator = "\n") {
                    "• $it"
                }
                backendMessage = buildString {
                    append("Red guardada: ${snapshot.ssid ?: "Nombre no disponible"}\n")
                    append("$risk\n")
                    if (reasons.isNotBlank()) {
                        append("$reasons\n")
                    }
                    append("Recibo: ${receipt.id}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backendError = true
                backendMessage = e.message ?: "No se pudo completar el envío."
            } finally {
                sending = false
            }
        }
    }

    private fun stopObservation() {
        wifiConnectionObserver.stop()
    }

    private fun restartObservation() {
        stopObservation()
        backendMessage = null
        connection = null
        permissionGranted = ContextCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val location = getSystemService(LocationManager::class.java)
        locationEnabled = if (Build.VERSION.SDK_INT >= 28) location.isLocationEnabled
            else location.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        status = "Sin conexión Wi-Fi detectada. Conéctate a una red para comenzar."
        wifiConnectionObserver.start(
            hasLocationPermission = permissionGranted
        )
    }
}
