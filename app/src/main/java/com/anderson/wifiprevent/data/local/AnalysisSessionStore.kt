package com.anderson.wifiprevent.data.local

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.anderson.wifiprevent.domain.model.AnalysisSessionState
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import kotlin.math.max

data class StoredAnalysisSession(
    val id: String,
    val ssid: String?,
    val state: AnalysisSessionState,
    val metrics: TrafficMetrics
)

class AnalysisSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "analysis_session",
        Context.MODE_PRIVATE
    )

    fun start(sessionId: String, ssid: String?) {
        preferences.edit()
            .putString("session_id", sessionId)
            .putString("ssid", ssid)
            .putString("state", AnalysisSessionState.ANALYZING.name)
            .putLong("started_at", SystemClock.elapsedRealtime())
            .putLong("base_rx_bytes", counter(TrafficStats.getTotalRxBytes()))
            .putLong("base_tx_bytes", counter(TrafficStats.getTotalTxBytes()))
            .putLong("base_rx_packets", counter(TrafficStats.getTotalRxPackets()))
            .putLong("base_tx_packets", counter(TrafficStats.getTotalTxPackets()))
            .remove("final_duration")
            .remove("final_rx_bytes")
            .remove("final_tx_bytes")
            .remove("final_rx_packets")
            .remove("final_tx_packets")
            .commit()
    }

    fun complete(): StoredAnalysisSession? {
        val current = snapshot() ?: return null
        preferences.edit()
            .putString("state", AnalysisSessionState.COMPLETED.name)
            .putLong("final_duration", current.metrics.durationSeconds)
            .putLong("final_rx_bytes", current.metrics.receivedBytes)
            .putLong("final_tx_bytes", current.metrics.transmittedBytes)
            .putLong("final_rx_packets", current.metrics.receivedPackets)
            .putLong("final_tx_packets", current.metrics.transmittedPackets)
            .commit()
        return current.copy(state = AnalysisSessionState.COMPLETED)
    }

    fun clear() {
        preferences.edit().clear().commit()
    }

    fun snapshot(): StoredAnalysisSession? {
        val id = preferences.getString("session_id", null) ?: return null
        val state = runCatching {
            AnalysisSessionState.valueOf(
                preferences.getString("state", AnalysisSessionState.FAILED.name)!!
            )
        }.getOrDefault(AnalysisSessionState.FAILED)
        val metrics = if (state == AnalysisSessionState.COMPLETED) {
            TrafficMetrics(
                preferences.getLong("final_duration", 0),
                preferences.getLong("final_rx_bytes", 0),
                preferences.getLong("final_tx_bytes", 0),
                preferences.getLong("final_rx_packets", 0),
                preferences.getLong("final_tx_packets", 0)
            )
        } else {
            liveMetrics()
        }
        return StoredAnalysisSession(
            id,
            preferences.getString("ssid", null),
            state,
            metrics
        )
    }

    private fun liveMetrics(): TrafficMetrics {
        val startedAt = preferences.getLong("started_at", SystemClock.elapsedRealtime())
        return TrafficMetrics(
            durationSeconds = max(0, SystemClock.elapsedRealtime() - startedAt) / 1_000,
            receivedBytes = delta(TrafficStats.getTotalRxBytes(), "base_rx_bytes"),
            transmittedBytes = delta(TrafficStats.getTotalTxBytes(), "base_tx_bytes"),
            receivedPackets = delta(TrafficStats.getTotalRxPackets(), "base_rx_packets"),
            transmittedPackets = delta(TrafficStats.getTotalTxPackets(), "base_tx_packets")
        )
    }

    private fun delta(value: Long, baselineKey: String): Long {
        val current = counter(value)
        return max(0, current - preferences.getLong(baselineKey, current))
    }

    private fun counter(value: Long): Long =
        if (value == TrafficStats.UNSUPPORTED.toLong()) 0 else max(0, value)
}
