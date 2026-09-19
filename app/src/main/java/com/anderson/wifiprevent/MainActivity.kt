package com.anderson.wifiprevent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.ui.connection.ConnectionScreen
import com.anderson.wifiprevent.ui.history.HistoryScreen
import com.anderson.wifiprevent.ui.theme.WifiPreventTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    private val backend by lazy { BackendClient(applicationContext) }
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val networks = linkedMapOf<Network, WifiSnapshot>()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
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
                val page = backend.history(cursor)
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
                val receipt = backend.send(snapshot)
                backendMessage = "Red enviada: ${snapshot.ssid ?: "Nombre no disponible"}\n$receipt"
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
        callback?.let { connectivity.unregisterNetworkCallback(it) }
        callback = null
        networks.clear()
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
        val flags = if (Build.VERSION.SDK_INT >= 31 && permissionGranted)
            ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO else 0
        val observer = object : ConnectivityManager.NetworkCallback(flags) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (callback !== this) return
                @Suppress("DEPRECATION")
                val info = if (Build.VERSION.SDK_INT >= 31) caps.transportInfo as? WifiInfo
                    else applicationContext.getSystemService(WifiManager::class.java).connectionInfo
                val next = WifiSnapshot(
                    ssid = info?.ssid?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.isBlank() }
                        ?.removeSurrounding("\""),
                    rssi = info?.rssi?.takeIf { it in -126..-1 },
                    frequency = info?.frequency?.takeIf { it > 0 },
                    speed = info?.linkSpeed?.takeIf { it > 0 },
                    internetValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
                )
                networks[network] = next
                if (connection?.ssid != next.ssid) backendMessage = null
                connection = next
                status = "Conexión Wi-Fi detectada"
            }
            override fun onLost(network: Network) {
                if (callback !== this) return
                networks.remove(network)
                connection = networks.values.lastOrNull()
                backendMessage = null
                if (connection == null) status = "La conexión Wi-Fi se perdió."
            }
        }
        callback = observer
        try {
            connectivity.registerNetworkCallback(NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(), observer, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
            callback = null
            status = "Android no permitió consultar la conexión. Revisa los permisos de la aplicación."
        }
    }
}