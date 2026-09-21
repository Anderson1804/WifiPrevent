package com.anderson.wifiprevent.data.remote

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.anderson.wifiprevent.BuildConfig
import com.anderson.wifiprevent.data.local.InstallationStore
import com.anderson.wifiprevent.domain.model.ConnectionReceipt
import com.anderson.wifiprevent.domain.model.AnalysisReceipt
import com.anderson.wifiprevent.data.local.StoredAnalysisSession
import com.anderson.wifiprevent.domain.model.HistoryEntry
import com.anderson.wifiprevent.domain.model.HistoryPage
import com.anderson.wifiprevent.domain.model.WifiSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

// Desarrollo local; una versión publicada debe usar HTTPS y autenticación real.
class BackendClient(context: Context) {
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val installationStore =
        InstallationStore(context)

    private val backendBaseUrl = if (isRunningOnEmulator()) {
        "http://10.0.2.2:8001"
    } else {
        "http://${BuildConfig.LOCAL_BACKEND_HOST}:8001"
    }

    suspend fun send(snapshot: WifiSnapshot): ConnectionReceipt = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("ssid", snapshot.ssid ?: JSONObject.NULL)
            put("rssi_dbm", snapshot.rssi ?: JSONObject.NULL)
            put("frequency_mhz", snapshot.frequency ?: JSONObject.NULL)
            put("link_speed_mbps", snapshot.speed ?: JSONObject.NULL)
            put("internet_validated", snapshot.internetValidated)
            put("captive_portal", snapshot.captivePortal)
            put(
                "security_type",
                snapshot.securityType ?: JSONObject.NULL
            )
        }.toString()
        // Keep the identifier across retries (including app restarts) after a lost response.
        val requestId =
            installationStore.requestIdFor(payload)
        val reply = request("POST", "/api/v1/connection-checks", payload, requestId)
        require(
            reply.getString("status") == "received" &&
                    reply.has("analysis_performed") &&
                    reply.has("risk_level")
        ) {
            "El servidor devolvió una respuesta inesperada."
        }
        installationStore.clearPendingRequest()
        ConnectionReceipt(
            id = reply.getString("receipt_id"),
            message = reply.getString("message"),
            riskLevel = reply.nullableString("risk_level"),
            riskReasons = reply.stringList("risk_reasons"),
            analysisPerformed = reply.getBoolean("analysis_performed")
        )
    }

    suspend fun history(before: String? = null): HistoryPage = withContext(Dispatchers.IO) {
        val cursor = before?.let { "&before=${UUID.fromString(it)}" } ?: ""
        val response = request("GET", "/api/v1/connection-checks?limit=20$cursor")
        val array = response.getJSONArray("items")
        val entries = (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            HistoryEntry(
                row.getString("receipt_id"),
                row.getString("received_at"),
                row.nullableString("ssid"),
                row.nullableInt("rssi_dbm"),
                row.nullableInt("frequency_mhz"),
                row.nullableInt("link_speed_mbps"),
                row.getBoolean("internet_validated"),
                row.getBoolean("captive_portal"),
                row.nullableString("security_type"),
                row.nullableString("risk_level"),
                row.stringList("risk_reasons"),
                row.getBoolean("analysis_performed")
            )
        }
        HistoryPage(entries, response.nullableString("next_before"))
    }

    private fun request(method: String, path: String, payload: String? = null,
                        requestId: String? = null): JSONObject {
        check(debug) { "El servidor de producción todavía no está configurado." }
        val connection = URL("$backendBaseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 5000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty(
                "Authorization",
                "Bearer ${installationStore.token}"
            )
            requestId?.let { connection.setRequestProperty("X-Request-ID", it) }
            payload?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { stream -> stream.write(it.toByteArray(Charsets.UTF_8)) }
            }
            when (val code = connection.responseCode) {
                200 -> return JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                401 -> error("No se pudo identificar esta instalación. Revisa la versión del backend.")
                404 -> error("El registro ya no está disponible. Actualiza el historial.")
                409 -> error("El identificador del envío tiene otros datos. No se guardó un duplicado.")
                422 -> error("El servidor rechazó los datos. Revisa la versión de la aplicación.")
                503 -> error("La base de datos no está disponible. Inicia PostgreSQL y vuelve a intentar.")
                else -> error("El servidor respondió con un error ($code). Inténtalo de nuevo.")
            }
        } catch (_: SocketTimeoutException) {
            throw IOException("El servidor tardó demasiado. Puedes reintentar el mismo envío sin duplicarlo.")
        } catch (e: IOException) {
            throw IOException(
                "No se pudo conectar al servidor local. Comprueba que el backend esté " +
                        "iniciado y que el teléfono y la PC usen la misma red.",
                e
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun sendAnalysis(session: StoredAnalysisSession): AnalysisReceipt =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("session_id", session.id)
                put("ssid", session.ssid ?: JSONObject.NULL)
                put("security_type", session.securityType ?: JSONObject.NULL)
                put("duration_seconds", session.metrics.durationSeconds)
                put("received_bytes", session.metrics.receivedBytes)
                put("transmitted_bytes", session.metrics.transmittedBytes)
                put("received_packets", session.metrics.receivedPackets)
                put("transmitted_packets", session.metrics.transmittedPackets)
            }.toString()
            val reply = request("POST", "/api/v1/analysis-sessions", payload)
            require(reply.getString("status") == "completed") {
                "El servidor devolvió una respuesta inesperada para la sesión."
            }
            AnalysisReceipt(
                sessionId = reply.getString("session_id"),
                message = reply.getString("message")
            )
        }

    private fun isRunningOnEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for")
    }
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun JSONObject.stringList(key: String): List<String> {
    if (!has(key) || isNull(key)) {
        return emptyList()
    }

    val values = getJSONArray(key)

    return (0 until values.length()).map { index ->
        values.getString(index)
    }
}
