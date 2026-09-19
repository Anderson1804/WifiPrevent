package com.anderson.wifiprevent.data.remote

import android.content.Context
import android.content.pm.ApplicationInfo
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
import com.anderson.wifiprevent.data.local.InstallationStore

// Local emulator only; release must configure HTTPS and real user authentication.
class BackendClient(context: Context) {
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val installationStore =
        InstallationStore(context)

    suspend fun send(snapshot: WifiSnapshot): String = withContext(Dispatchers.IO) {
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
        "${reply.getString("message")}\nRecibo: ${reply.getString("receipt_id")}"
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
        val connection = URL("http://10.0.2.2:8001$path").openConnection() as HttpURLConnection
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
            throw IOException("No se pudo conectar al servidor local. Inicia el backend en la PC y usa el emulador.", e)
        } finally {
            connection.disconnect()
        }
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
