package com.anderson.wifiprevent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.VpnService
import android.net.Uri
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
import com.anderson.wifiprevent.data.local.AnalysisSessionStore
import com.anderson.wifiprevent.data.vpn.TrafficAnalysisService
import com.anderson.wifiprevent.data.vpn.SocksRelayProbe
import com.anderson.wifiprevent.data.vpn.SocksRelayStatus
import com.anderson.wifiprevent.domain.model.AnalysisSession
import com.anderson.wifiprevent.domain.model.AnalysisSessionState
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.ui.connection.ConnectionScreen
import com.anderson.wifiprevent.ui.analysis.AnalysisScreen
import com.anderson.wifiprevent.ui.analysis.AnalysisHistoryScreen
import com.anderson.wifiprevent.ui.history.HistoryScreen
import com.anderson.wifiprevent.ui.common.formatRiskLevel
import com.anderson.wifiprevent.ui.theme.WifiPreventTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.anderson.wifiprevent.data.remote.BackendClient
import com.anderson.wifiprevent.data.repository.ConnectionRepository

class MainActivity : ComponentActivity() {
    private var currentScreen by mutableStateOf(AppScreen.CONNECTION)
    private var analysisSession by mutableStateOf<AnalysisSession?>(null)
    private var analysisMetrics by mutableStateOf(TrafficMetrics.EMPTY)
    private var analysisMetadata by mutableStateOf(TrafficMetadataSummary.EMPTY)
    private val analysisTimer = Handler(Looper.getMainLooper())
    private val analysisTick = object : Runnable {
        override fun run() {
            val snapshot = analysisSessionStore.snapshot()
            if (snapshot != null && analysisSession?.id == snapshot.id) {
                analysisMetrics = snapshot.metrics
                analysisMetadata = snapshot.metadata
                analysisSession = analysisSession?.copy(state = snapshot.state)
            }
            if (snapshot?.state == AnalysisSessionState.ANALYZING) {
                analysisTimer.postDelayed(this, 1_000)
            } else if (snapshot?.state == AnalysisSessionState.COMPLETED) {
                uploadCompletedAnalysis()
            }
        }
    }
    private var historyEntries by mutableStateOf<List<HistoryEntry>>(emptyList())
    private var historyLoading by mutableStateOf(false)
    private var historyError by mutableStateOf<String?>(null)
    private var historyCursor: String? = null
    private var historyHasMore by mutableStateOf(false)
    private var analysisHistoryEntries by mutableStateOf<List<AnalysisHistoryEntry>>(emptyList())
    private var analysisHistoryLoading by mutableStateOf(false)
    private var analysisHistoryError by mutableStateOf<String?>(null)
    private var analysisHistoryCursor: String? = null
    private var analysisHistoryHasMore by mutableStateOf(false)
    private var connection by mutableStateOf<WifiSnapshot?>(null)
    private var permissionGranted by mutableStateOf(false)
    private var locationEnabled by mutableStateOf(false)
    private var status by mutableStateOf("Buscando una conexión Wi-Fi…")
    private var sending by mutableStateOf(false)
    private var backendMessage by mutableStateOf<String?>(null)
    private var backendError by mutableStateOf(false)
    private var submission: Job? = null
    private var analysisUploading by mutableStateOf(false)
    private var analysisUploadMessage by mutableStateOf<String?>(null)
    private var analysisUploadError by mutableStateOf(false)
    private var relayStatus by mutableStateOf<SocksRelayStatus?>(null)
    private var checkingRelay by mutableStateOf(false)
    private val socksRelayProbe = SocksRelayProbe()

    private val connectionRepository by lazy {
        ConnectionRepository(
            backendClient = BackendClient(applicationContext)
        )
    }

    private val wifiConnectionObserver by lazy {
        WifiConnectionObserver(applicationContext) { newConnection, newStatus ->
            if (
                connection != null &&
                connection?.ssid != newConnection?.ssid &&
                analysisSession?.state != AnalysisSessionState.ANALYZING
            ) {
                backendMessage = null
                analysisSession = null
            }

            connection = newConnection
            status = newStatus
        }
    }
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (currentScreen == AppScreen.HISTORY) {
            historyError = if (granted) "Permiso concedido. Pulsa Actualizar historial."
                else "Permiso de red local requerido. Habilítalo desde los permisos de la aplicación."
        }
        if (currentScreen == AppScreen.ANALYSIS_HISTORY) {
            analysisHistoryError = if (granted) "Permiso concedido. Pulsa Actualizar análisis."
                else "Permiso de red local requerido. Habilítalo desde los permisos de la aplicación."
        }
        backendError = !granted
        backendMessage = if (granted) "Permiso concedido. Pulsa Guardar consulta."
            else "El envío requiere permiso de red local. Puedes habilitarlo en los permisos de la aplicación."
    }
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { restartObservation() }
    private val vpnPermissionRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startAnalysisService()
        } else {
            analysisSession = analysisSession?.copy(
                state = AnalysisSessionState.FAILED
            )
        }
    }
    private val analysisSessionStore by lazy {
        AnalysisSessionStore(applicationContext)
    }
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestVpnPermission()
        } else {
            analysisSession = analysisSession?.copy(
                state = AnalysisSessionState.FAILED
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreAnalysisSession()
        enableEdgeToEdge()
        setContent {
            WifiPreventTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    BackHandler(enabled = currentScreen != AppScreen.CONNECTION) {
                        currentScreen = AppScreen.CONNECTION
                    }
                    when (currentScreen) {
                        AppScreen.ANALYSIS_HISTORY -> AnalysisHistoryScreen(
                            entries = analysisHistoryEntries,
                            loading = analysisHistoryLoading,
                            error = analysisHistoryError,
                            hasMore = analysisHistoryHasMore,
                            onBack = { currentScreen = AppScreen.CONNECTION },
                            onRefresh = { loadAnalysisHistory() },
                            onMore = { loadAnalysisHistory(more = true) },
                            modifier = Modifier.padding(padding)
                        )

                        AppScreen.HISTORY -> HistoryScreen(
                            historyEntries,
                            historyLoading,
                            historyError,
                            historyHasMore,
                            onBack = { currentScreen = AppScreen.CONNECTION },
                            onRefresh = { loadHistory() },
                            onMore = { loadHistory(more = true) },
                            modifier = Modifier.padding(padding)
                        )

                        AppScreen.ANALYSIS -> AnalysisScreen(
                            wifi = connection,
                            session = analysisSession,
                            metrics = analysisMetrics,
                            metadata = analysisMetadata,
                            uploadMessage = analysisUploadMessage,
                            uploadError = analysisUploadError,
                            uploading = analysisUploading,
                            relayStatus = relayStatus,
                            checkingRelay = checkingRelay,
                            onBack = { currentScreen = AppScreen.CONNECTION },
                            onPrepare = { prepareAnalysisSession() },
                            onStart = { beginAnalysisAuthorization() },
                            onStop = { stopAnalysisService() },
                            onRetryUpload = { uploadCompletedAnalysis() },
                            onCheckRelay = { checkSocksRelay() },
                            modifier = Modifier.padding(padding)
                        )

                        AppScreen.CONNECTION -> ConnectionScreen(
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
                        onAnalysis = { currentScreen = AppScreen.ANALYSIS },
                        onHistory = {
                            currentScreen = AppScreen.HISTORY
                            loadHistory()
                        },
                        onAnalysisHistory = {
                            currentScreen = AppScreen.ANALYSIS_HISTORY
                            loadAnalysisHistory()
                        },
                        modifier = Modifier.padding(padding)
                    )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        restartObservation()
        refreshAnalysisSession()
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

    private fun loadAnalysisHistory(more: Boolean = false) {
        if (analysisHistoryLoading) return
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(
                this, "android.permission.ACCESS_LOCAL_NETWORK"
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            localNetworkPermission.launch("android.permission.ACCESS_LOCAL_NETWORK")
            return
        }
        val cursor = if (more) analysisHistoryCursor else null
        if (more && cursor == null) return
        analysisHistoryLoading = true
        analysisHistoryError = null
        lifecycleScope.launch {
            try {
                val page = connectionRepository.getAnalysisHistory(cursor)
                analysisHistoryEntries = if (more) {
                    (analysisHistoryEntries + page.entries).distinctBy { it.id }
                } else {
                    page.entries
                }
                analysisHistoryCursor = page.nextBefore
                analysisHistoryHasMore = page.nextBefore != null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analysisHistoryError = e.message ?: "No se pudo consultar el historial de análisis."
            } finally {
                analysisHistoryLoading = false
            }
        }
    }

    override fun onDestroy() {
        analysisTimer.removeCallbacks(analysisTick)
        super.onDestroy()
    }

    private fun prepareAnalysisSession() {
        analysisTimer.removeCallbacks(analysisTick)
        analysisSessionStore.clear()
        analysisMetrics = TrafficMetrics.EMPTY
        analysisMetadata = TrafficMetadataSummary.EMPTY
        analysisUploadMessage = null
        analysisUploadError = false
        analysisSession = AnalysisSession(
            state = AnalysisSessionState.READY,
            ssid = connection?.ssid
        )
    }

    private fun checkSocksRelay() {
        if (checkingRelay) return
        checkingRelay = true
        lifecycleScope.launch {
            relayStatus = socksRelayProbe.check()
            checkingRelay = false
        }
    }

    private fun beginAnalysisAuthorization() {
        analysisSession = analysisSession?.copy(
            state = AnalysisSessionState.PREPARING
        )

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            requestVpnPermission()
        }
    }

    private fun requestVpnPermission() {
        val preparationIntent = VpnService.prepare(this)
        if (preparationIntent == null) {
            startAnalysisService()
        } else {
            vpnPermissionRequest.launch(preparationIntent)
        }
    }

    private fun startAnalysisService() {
        val intent = Intent(this, TrafficAnalysisService::class.java).apply {
            action = TrafficAnalysisService.ACTION_START
            putExtra(
                TrafficAnalysisService.EXTRA_NETWORK_NAME,
                connection?.ssid
            )
            putExtra(
                TrafficAnalysisService.EXTRA_SESSION_ID,
                analysisSession?.id
            )
            putExtra(
                TrafficAnalysisService.EXTRA_SECURITY_TYPE,
                connection?.securityType
            )
        }
        ContextCompat.startForegroundService(this, intent)

        analysisMetrics = TrafficMetrics.EMPTY
        analysisMetadata = TrafficMetadataSummary.EMPTY
        analysisSession = analysisSession?.copy(
            state = AnalysisSessionState.ANALYZING
        )
        analysisTimer.removeCallbacks(analysisTick)
        analysisTimer.postDelayed(analysisTick, 250)
    }

    private fun stopAnalysisService() {
        val intent = Intent(this, TrafficAnalysisService::class.java).apply {
            action = TrafficAnalysisService.ACTION_STOP
        }
        startService(intent)
        analysisTimer.postDelayed(analysisTick, 250)
    }

    private fun restoreAnalysisSession() {
        val snapshot = analysisSessionStore.snapshot() ?: return
        analysisSession = AnalysisSession(
            id = snapshot.id,
            state = snapshot.state,
            ssid = snapshot.ssid
        )
        analysisMetrics = snapshot.metrics
        analysisMetadata = snapshot.metadata
        if (snapshot.state == AnalysisSessionState.ANALYZING) {
            analysisTimer.post(analysisTick)
        } else if (snapshot.state == AnalysisSessionState.COMPLETED) {
            if (snapshot.uploaded) {
                analysisUploadMessage = "Sesión guardada en el servidor local."
            } else {
                uploadCompletedAnalysis()
            }
        }
    }

    private fun refreshAnalysisSession() {
        val snapshot = analysisSessionStore.snapshot() ?: return
        if (analysisSession?.id != snapshot.id) {
            restoreAnalysisSession()
        } else {
            analysisMetrics = snapshot.metrics
            analysisMetadata = snapshot.metadata
            analysisSession = analysisSession?.copy(state = snapshot.state)
            if (snapshot.state == AnalysisSessionState.ANALYZING) {
                analysisTimer.removeCallbacks(analysisTick)
                analysisTimer.post(analysisTick)
            } else if (snapshot.state == AnalysisSessionState.COMPLETED) {
                if (snapshot.uploaded) {
                    analysisUploadMessage = "Sesión guardada en el servidor local."
                    analysisUploadError = false
                } else {
                    uploadCompletedAnalysis()
                }
            }
        }
    }

    private fun uploadCompletedAnalysis() {
        val stored = analysisSessionStore.snapshot() ?: return
        if (
            stored.state != AnalysisSessionState.COMPLETED ||
            stored.uploaded ||
            analysisUploading
        ) return

        analysisUploading = true
        analysisUploadError = false
        analysisUploadMessage = "Guardando la sesión en el servidor local…"
        lifecycleScope.launch {
            try {
                val receipt = connectionRepository.saveAnalysis(stored)
                analysisSessionStore.markUploaded(receipt.sessionId)
                val reasons = receipt.riskReasons.joinToString(separator = "\n") { "• $it" }
                analysisUploadMessage = buildString {
                    append("${receipt.message}\n")
                    append("Evaluación preliminar: ${formatRiskLevel(receipt.riskLevel, true)}")
                    if (reasons.isNotBlank()) append("\n$reasons")
                    if (!receipt.trafficAnalysisPerformed) {
                        append("\nLa muestra de protocolos es controlada; la captura completa está pendiente.")
                    }
                    receipt.indicators.forEach { indicator ->
                        append("\n${indicator.title}: ${indicator.description}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analysisUploadError = true
                analysisUploadMessage =
                    "La sesión quedó guardada en el teléfono, pero aún no llegó al servidor. " +
                            (e.message ?: "Puedes reintentar el envío.")
            } finally {
                analysisUploading = false
            }
        }
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

private enum class AppScreen {
    CONNECTION,
    ANALYSIS,
    ANALYSIS_HISTORY,
    HISTORY
}
