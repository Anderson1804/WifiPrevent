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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.anderson.wifiprevent.data.vpn.developmentRelayHost
import com.anderson.wifiprevent.domain.model.AnalysisSession
import com.anderson.wifiprevent.domain.model.AnalysisSessionState
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.domain.model.AnalysisHistoryEntry
import com.anderson.wifiprevent.domain.model.AnalysisHistorySummary
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.ui.connection.ConnectionScreen
import com.anderson.wifiprevent.ui.analysis.AnalysisScreen
import com.anderson.wifiprevent.ui.analysis.AnalysisHistoryScreen
import com.anderson.wifiprevent.ui.analysis.formatAnalysisReport
import com.anderson.wifiprevent.ui.history.HistoryScreen
import com.anderson.wifiprevent.ui.history.ConnectionHistoryList
import com.anderson.wifiprevent.ui.history.HistoryCategory
import com.anderson.wifiprevent.ui.common.AppIcons
import com.anderson.wifiprevent.domain.model.AnalysisReceipt
import com.anderson.wifiprevent.domain.model.ConnectionReceipt
import com.anderson.wifiprevent.ui.theme.WifiPreventTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.anderson.wifiprevent.data.remote.BackendClient
import com.anderson.wifiprevent.data.repository.ConnectionRepository

class MainActivity : ComponentActivity() {
    private var currentScreen by mutableStateOf(AppScreen.CONNECTION)
    private var historyCategory by mutableStateOf(HistoryCategory.ANALYSES)
    private var analysisReceipt by mutableStateOf<AnalysisReceipt?>(null)
    private var connectionReceipt by mutableStateOf<ConnectionReceipt?>(null)
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
                analysisSession = analysisSession?.copy(
                    state = snapshot.state,
                    captureMode = snapshot.captureMode
                )
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
    private var analysisHistorySummary by mutableStateOf<AnalysisHistorySummary?>(null)
    private var analysisHistoryRiskFilter by mutableStateOf<String?>(null)
    private var analysisHistoryModeFilter by mutableStateOf<String?>(null)
    private var analysisHistoryDeletingId by mutableStateOf<String?>(null)
    private var analysisHistoryLoading by mutableStateOf(false)
    private var analysisHistoryError by mutableStateOf<String?>(null)
    private var analysisHistoryCursor: String? = null
    private var analysisHistoryHasMore by mutableStateOf(false)
    private var analysisHistoryExportingId by mutableStateOf<String?>(null)
    private var analysisHistoryExportMessage by mutableStateOf<String?>(null)
    private var pendingTrainingCsv: String? = null
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
    private val socksRelayProbe = SocksRelayProbe(developmentRelayHost())

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
            val message = if (granted) "Permiso concedido. Pulsa Actualizar."
                else "Activa el permiso de red local para consultar el historial."
            if (historyCategory == HistoryCategory.ANALYSES) analysisHistoryError = message
            else historyError = message
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

    private val trainingCsvDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> saveExperimentDocument(uri) }

    private val experimentJsonDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> saveExperimentDocument(uri) }

    private fun saveExperimentDocument(uri: android.net.Uri?) {
        val csv = pendingTrainingCsv
        pendingTrainingCsv = null
        if (uri == null || csv == null) {
            analysisHistoryExportingId = null
            analysisHistoryExportMessage = if (uri == null) "Exportación cancelada."
                else "La exportación no pudo recuperarse. Vuelve a intentarlo."
        } else {
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = contentResolver.openOutputStream(uri, "wt")
                            ?: error("No se pudo abrir el archivo elegido.")
                        stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    }
                    analysisHistoryExportMessage =
                        "Archivo guardado. Completa y verifica las referencias del laboratorio antes de evaluar."
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    analysisHistoryError = "No se pudo escribir el archivo. Vuelve a exportar la sesión."
                } finally {
                    analysisHistoryExportingId = null
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", currentScreen.name)
        outState.putString("history_category", historyCategory.name)
        outState.putString("training_csv", pendingTrainingCsv)
        outState.putString("training_csv_session", analysisHistoryExportingId)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentScreen = runCatching { AppScreen.valueOf(savedInstanceState?.getString("screen") ?: "CONNECTION") }
            .getOrDefault(AppScreen.CONNECTION)
        historyCategory = runCatching { HistoryCategory.valueOf(savedInstanceState?.getString("history_category") ?: "ANALYSES") }
            .getOrDefault(HistoryCategory.ANALYSES)
        pendingTrainingCsv = savedInstanceState?.getString("training_csv")
        if (pendingTrainingCsv != null) {
            analysisHistoryExportingId = savedInstanceState?.getString("training_csv_session")
            currentScreen = AppScreen.HISTORY
            historyCategory = HistoryCategory.ANALYSES
        }
        restoreAnalysisSession()
        if (currentScreen == AppScreen.HISTORY) {
            if (historyCategory == HistoryCategory.ANALYSES) loadAnalysisHistory() else loadHistory()
        }
        enableEdgeToEdge()
        setContent {
            WifiPreventTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(selected = currentScreen == AppScreen.CONNECTION,
                                onClick = { currentScreen = AppScreen.CONNECTION },
                                icon = { Icon(AppIcons.Connection, contentDescription = null) }, label = { Text("Inicio") })
                            NavigationBarItem(selected = currentScreen == AppScreen.ANALYSIS,
                                onClick = { openAnalysis() },
                                icon = { Icon(AppIcons.Analysis, contentDescription = null) }, label = { Text("Análisis") })
                            NavigationBarItem(selected = currentScreen == AppScreen.HISTORY,
                                onClick = { openHistory() },
                                icon = { Icon(AppIcons.History, contentDescription = null) }, label = { Text("Historial") })
                        }
                    }
                ) { padding ->
                    BackHandler(enabled = currentScreen != AppScreen.CONNECTION) { currentScreen = AppScreen.CONNECTION }
                    when (currentScreen) {
                        AppScreen.HISTORY -> HistoryScreen(
                            category = historyCategory,
                            onCategoryChange = { category -> openHistory(category) },
                            modifier = Modifier.padding(padding)
                        ) {
                            if (historyCategory == HistoryCategory.ANALYSES) {
                                AnalysisHistoryScreen(
                                    entries = analysisHistoryEntries, summary = analysisHistorySummary,
                                    riskFilter = analysisHistoryRiskFilter, captureModeFilter = analysisHistoryModeFilter,
                                    deletingId = analysisHistoryDeletingId, loading = analysisHistoryLoading,
                                    error = analysisHistoryError, hasMore = analysisHistoryHasMore,
                                    onRefresh = { loadAnalysisHistory() }, onMore = { loadAnalysisHistory(more = true) },
                                    onRiskFilterChange = { value ->
                                        analysisHistoryRiskFilter = value
                                        analysisHistoryEntries = emptyList()
                                        analysisHistoryCursor = null
                                        analysisHistoryHasMore = false
                                        loadAnalysisHistory()
                                    },
                                    onCaptureModeFilterChange = { value ->
                                        analysisHistoryModeFilter = value
                                        analysisHistoryEntries = emptyList()
                                        analysisHistoryCursor = null
                                        analysisHistoryHasMore = false
                                        loadAnalysisHistory()
                                    },
                                    onDelete = { sessionId -> deleteAnalysisHistoryEntry(sessionId) },
                                    onShare = { entry -> shareAnalysisReport(entry) },
                                    exportingId = analysisHistoryExportingId, exportMessage = analysisHistoryExportMessage,
                                    onExport = { entry -> exportTrainingObservation(entry) },
                                    onExportTemporal = { entry -> exportTrainingObservation(entry, temporal = true) },
                                    onExportExperiment = { entry -> exportTrainingObservation(entry, experiment = true) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                ConnectionHistoryList(entries = historyEntries, loading = historyLoading,
                                    error = historyError, hasMore = historyHasMore,
                                    onRefresh = { loadHistory() }, onMore = { loadHistory(more = true) },
                                    modifier = Modifier.weight(1f))
                            }
                        }
                        AppScreen.ANALYSIS -> AnalysisScreen(
                            wifi = connection, session = analysisSession, metrics = analysisMetrics,
                            metadata = analysisMetadata, receipt = analysisReceipt,
                            uploadMessage = analysisUploadMessage, uploadError = analysisUploadError,
                            uploading = analysisUploading, relayStatus = relayStatus, checkingRelay = checkingRelay,
                            fullModeSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                            onHistory = { openHistory(HistoryCategory.ANALYSES) },
                            onPrepare = { prepareAnalysisSession(); checkSocksRelay() },
                            onStartControlled = { beginAnalysisAuthorization(CaptureMode.CONTROLLED) },
                            onStartFull = { beginAnalysisAuthorization(CaptureMode.FULL) },
                            onStop = { stopAnalysisService() }, onRetryUpload = { uploadCompletedAnalysis() },
                            onCheckRelay = { checkSocksRelay() }, modifier = Modifier.padding(padding)
                        )
                        AppScreen.CONNECTION -> ConnectionScreen(
                            wifi = connection, permission = permissionGranted, location = locationEnabled,
                            status = status, sending = sending, backendMessage = backendMessage, backendError = backendError,
                            analysisActive = analysisSession?.state == AnalysisSessionState.ANALYZING ||
                                analysisSession?.state == AnalysisSessionState.PREPARING,
                            receipt = connectionReceipt,
                            onPermission = { permissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            onSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                            onLocation = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                            onWifi = { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
                            onCheck = { sendConnection() }, onAnalysis = { openAnalysis() }, modifier = Modifier.padding(padding)
                        )
                    }
                }
            }
        }
    }

    private fun openHistory(category: HistoryCategory = historyCategory) {
        currentScreen = AppScreen.HISTORY
        historyCategory = category
        if (category == HistoryCategory.ANALYSES) loadAnalysisHistory() else loadHistory()
    }

    private fun openAnalysis() {
        currentScreen = AppScreen.ANALYSIS
        if (analysisSession == null) prepareAnalysisSession()
        if (analysisSession?.state == AnalysisSessionState.READY) checkSocksRelay()
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
                connectionReceipt = receipt
                backendMessage = "Consulta guardada."
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
                if (!more) {
                    analysisHistorySummary = connectionRepository.getAnalysisHistorySummary()
                }
                val page = connectionRepository.getAnalysisHistory(
                    before = cursor,
                    riskLevel = analysisHistoryRiskFilter,
                    captureMode = analysisHistoryModeFilter
                )
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

    private fun deleteAnalysisHistoryEntry(sessionId: String) {
        if (analysisHistoryDeletingId != null || analysisHistoryLoading ||
            analysisHistoryExportingId != null) return
        analysisHistoryDeletingId = sessionId
        analysisHistoryError = null
        lifecycleScope.launch {
            try {
                connectionRepository.deleteAnalysis(sessionId)
                analysisHistoryEntries = analysisHistoryEntries.filterNot { it.id == sessionId }
                try {
                    analysisHistorySummary = connectionRepository.getAnalysisHistorySummary()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    analysisHistoryError =
                        "La sesión se eliminó, pero el resumen no pudo actualizarse. " +
                                "Pulsa Actualizar análisis."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                analysisHistoryError = e.message ?: "No se pudo eliminar la sesión."
            } finally {
                analysisHistoryDeletingId = null
            }
        }
    }

    override fun onDestroy() {
        analysisTimer.removeCallbacks(analysisTick)
        super.onDestroy()
    }

    private fun prepareAnalysisSession() {
        if (analysisUploading || analysisSession?.state == AnalysisSessionState.ANALYZING ||
            analysisSession?.state == AnalysisSessionState.PREPARING ||
            analysisSessionStore.snapshot()?.let {
                it.state == AnalysisSessionState.COMPLETED && !it.uploaded
            } == true) return
        analysisReceipt = null
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

    private fun beginAnalysisAuthorization(mode: CaptureMode) {
        if (analysisSession?.state != AnalysisSessionState.READY || connection == null) return
        analysisSession = analysisSession?.copy(
            state = AnalysisSessionState.PREPARING,
            captureMode = mode
        )

        if (mode == CaptureMode.FULL) {
            val sessionId = analysisSession?.id ?: return
            analysisUploadMessage = "Preparando métricas privadas del relé…"
            lifecycleScope.launch {
                try {
                    connectionRepository.startRelayCapture(sessionId)
                    analysisUploadMessage = null
                    continueAnalysisAuthorization()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    analysisSession = analysisSession?.copy(state = AnalysisSessionState.READY)
                    analysisUploadError = true
                    analysisUploadMessage = e.message ?: "No se pudo preparar el relé local."
                }
            }
        } else {
            continueAnalysisAuthorization()
        }
    }

    private fun continueAnalysisAuthorization() {
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
            connection?.let {
                putExtra(TrafficAnalysisService.EXTRA_CAPTIVE_PORTAL, it.captivePortal)
            }
            putExtra(
                TrafficAnalysisService.EXTRA_CAPTURE_MODE,
                analysisSession?.captureMode?.apiValue
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
            ssid = snapshot.ssid,
            captureMode = snapshot.captureMode
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
            analysisSession = analysisSession?.copy(
                state = snapshot.state,
                captureMode = snapshot.captureMode
            )
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
                var relaySnapshotUnavailable = false
                val enriched = if (
                    stored.captureMode == CaptureMode.FULL && !stored.relayMetricsCollected
                ) {
                    val relayMetrics = try {
                        connectionRepository.getRelayCaptureMetrics(stored.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        relaySnapshotUnavailable = true
                        null
                    }
                    if (relayMetrics != null) {
                        analysisSessionStore.updateRelayMetrics(stored.id, relayMetrics)
                        stored.copy(
                            relayMetricsCollected = true,
                            relayMetrics = relayMetrics
                        )
                    } else stored
                } else stored
                val receipt = connectionRepository.saveAnalysis(enriched)
                analysisSessionStore.markUploaded(receipt.sessionId)
                analysisReceipt = receipt
                analysisUploadMessage = if (relaySnapshotUnavailable && !receipt.relayMetricsCollected) {
                    "Guardado en historial. Las categorías de tráfico no estaban disponibles."
                } else "Guardado en historial."
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

    private fun exportTrainingObservation(entry: AnalysisHistoryEntry, temporal: Boolean = false, experiment: Boolean = false) {
        if (analysisHistoryExportingId != null || analysisHistoryDeletingId != null ||
            analysisHistoryLoading) return
        analysisHistoryExportingId = entry.id
        analysisHistoryExportMessage = null
        analysisHistoryError = null
        lifecycleScope.launch {
            try {
                pendingTrainingCsv = connectionRepository.getTrainingObservationCsv(entry.id, temporal, experiment)
                if (experiment) experimentJsonDocument.launch("wifiprevent_registro.json")
                else trainingCsvDocument.launch(if (temporal) "wifiprevent_eventos.csv" else "wifiprevent_observacion.csv")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingTrainingCsv = null
                analysisHistoryError = e.message ?: "No se pudo preparar el CSV."
            } finally {
                if (pendingTrainingCsv == null) analysisHistoryExportingId = null
            }
        }
    }

    private fun shareAnalysisReport(entry: AnalysisHistoryEntry) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Resumen de análisis WiFiPrevent")
            putExtra(Intent.EXTRA_TEXT, formatAnalysisReport(entry))
        }
        startActivity(Intent.createChooser(intent, "Compartir resumen de análisis"))
    }
}

private enum class AppScreen {
    CONNECTION,
    ANALYSIS,
    HISTORY
}
