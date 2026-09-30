package com.anderson.wifiprevent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import com.anderson.wifiprevent.domain.model.*
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.ui.analysis.AnalysisHistoryScreen
import com.anderson.wifiprevent.ui.analysis.AnalysisScreen
import com.anderson.wifiprevent.ui.connection.ConnectionScreen
import com.anderson.wifiprevent.ui.theme.WifiPreventTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiAuditTest {
    @get:Rule val compose = createComposeRule()
    private val wifi = WifiSnapshot("Red de prueba", -50, 2447, 72, true, false, "WPA3_SAE")

    @Test
    fun homeKeepsConnectionDetailsCollapsedAndRemovesDuplicateHistoryLinks() {
        compose.setContent {
            AuditTheme {
                ConnectionScreen(wifi, true, true, "Conectado", false, null, false, false, null,
                    {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("2447 MHz").assertDoesNotExist()
        compose.onNodeWithText("Ver historial").assertDoesNotExist()
        compose.onNodeWithText("Ver análisis guardados").assertDoesNotExist()
        compose.onNodeWithText("Datos de conexión").performClick()
        compose.onNodeWithText("2447 MHz").assertIsDisplayed()
        compose.onNodeWithText("Datos de conexión").performClick()
        compose.onNodeWithText("2447 MHz").assertDoesNotExist()
    }

    @Test
    fun pendingSaveCanBeRetriedAndCannotBeReplacedByNewAnalysis() {
        var retries = 0
        var newSessions = 0
        compose.setContent {
            AuditTheme {
                AnalysisScreen(wifi, AnalysisSession(state = AnalysisSessionState.COMPLETED,
                    ssid = wifi.ssid, captureMode = CaptureMode.FULL), TrafficMetrics.EMPTY,
                    TrafficMetadataSummary.EMPTY, null, "Guardado pendiente", true, false,
                    null, false, true, {}, { newSessions++ }, {}, {}, {}, { retries++ }, {})
            }
        }
        compose.onNodeWithText("Nuevo análisis").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Reintentar guardado").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(0, newSessions) }
    }

    @Test
    fun adequateHistoryEntryKeepsExportBehindDetailsAndConfirmation() {
        var exports = 0
        val entry = entry(adequate = true)
        compose.setContent {
            AuditTheme {
                AnalysisHistoryScreen(listOf(entry), null, null, null, null, false, null, false,
                    {}, {}, {}, {}, {}, {}, null, null, { exports++ })
            }
        }
        compose.onNodeWithText("Riesgo bajo").assertIsDisplayed()
        screenshot("history-fixture-light.png")
        compose.onNodeWithText("Exportar métricas CSV").assertDoesNotExist()
        compose.onNodeWithText("Detalles y acciones").performClick()
        compose.onNodeWithText("Exportar métricas CSV").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, exports) }
        compose.onNodeWithText("Guardar archivo").performClick()
        compose.runOnIdle { assertEquals(1, exports) }
    }

    @Test
    fun insufficientHistoryEntryCannotExport() {
        compose.setContent {
            AuditTheme(darkTheme = true) {
                AnalysisHistoryScreen(listOf(entry(false)), null, null, null, null, false, null, false,
                    {}, {}, {}, {}, {}, {}, null, null, {})
            }
        }
        screenshot("history-fixture-dark.png")
        compose.onNodeWithText("Detalles y acciones").performClick()
        compose.onNodeWithText("Exportar métricas CSV").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun largerTextKeepsThePrimaryActionReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                AuditTheme(darkTheme = true) {
                    ConnectionScreen(wifi, true, true, "Conectado", false, null, false, false, null,
                        {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Analizar conexión").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("home-large-text-dark.png")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "ui-audit").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun entry(adequate: Boolean) = AnalysisHistoryEntry(
        id = "ui-test", receivedAt = "2026-09-29T12:00:00Z", ssid = wifi.ssid,
        securityType = "WPA3_SAE", captivePortal = false,
        metrics = TrafficMetrics(if (adequate) 30 else 5, 2048, 1024, 80, 20),
        metadata = TrafficMetadataSummary.EMPTY, riskLevel = "low",
        riskReasons = listOf("Cifrado moderno informado por Android."),
        assessmentScope = "connection_and_traffic_metadata", assessmentVersion = "rules-relay-v3",
        trafficAnalysisPerformed = true, captureMode = "full", indicators = emptyList(),
        sampleQuality = if (adequate) "adequate" else "limited",
        recommendations = listOf("Mantén HTTPS."), relayMetricsCollected = true,
        relayMetrics = RelayCaptureMetrics(4, 6, 2, 0, 8, 0, 3)
    )
}

@Composable
private fun AuditTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    WifiPreventTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}
