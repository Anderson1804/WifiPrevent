package com.anderson.wifiprevent.data.local

import android.content.Context
import android.net.TrafficStats
import android.os.SystemClock
import com.anderson.wifiprevent.domain.model.AnalysisSessionState
import com.anderson.wifiprevent.domain.model.TrafficMetrics
import com.anderson.wifiprevent.domain.traffic.TrafficMetadataSummary
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import com.anderson.wifiprevent.data.vpn.TunnelTrafficCounters
import kotlin.math.max

data class StoredAnalysisSession(
    val id: String,
    val ssid: String?,
    val securityType: String?,
    val state: AnalysisSessionState,
    val metrics: TrafficMetrics,
    val metadata: TrafficMetadataSummary,
    val captureMode: CaptureMode,
    val uploaded: Boolean
)

class AnalysisSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "analysis_session",
        Context.MODE_PRIVATE
    )

    fun start(sessionId: String, ssid: String?, securityType: String?, captureMode: CaptureMode) {
        preferences.edit()
            .putString("session_id", sessionId)
            .putString("ssid", ssid)
            .putString("security_type", securityType)
            .putString("capture_mode", captureMode.apiValue)
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
            .remove("tunnel_rx_bytes")
            .remove("tunnel_tx_bytes")
            .remove("tunnel_rx_packets")
            .remove("tunnel_tx_packets")
            .putBoolean("uploaded", false)
            .putMetadata(TrafficMetadataSummary.EMPTY)
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

    fun updateMetadata(summary: TrafficMetadataSummary) {
        preferences.edit().putMetadata(summary).apply()
    }

    fun updateTunnelCounters(counters: TunnelTrafficCounters) {
        if (preferences.getString("capture_mode", null) != CaptureMode.FULL.apiValue ||
            preferences.getString("state", null) != AnalysisSessionState.ANALYZING.name
        ) return
        preferences.edit()
            .putLong("tunnel_rx_bytes", counters.receivedBytes)
            .putLong("tunnel_tx_bytes", counters.transmittedBytes)
            .putLong("tunnel_rx_packets", counters.receivedPackets)
            .putLong("tunnel_tx_packets", counters.transmittedPackets)
            .apply()
    }

    fun fail() {
        preferences.edit().putString("state", AnalysisSessionState.FAILED.name).commit()
    }

    fun markUploaded(sessionId: String) {
        if (preferences.getString("session_id", null) == sessionId) {
            preferences.edit().putBoolean("uploaded", true).commit()
        }
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
            preferences.getString("security_type", null),
            state,
            metrics,
            metadataSnapshot(),
            CaptureMode.entries.firstOrNull {
                it.apiValue == preferences.getString("capture_mode", null)
            } ?: CaptureMode.CONTROLLED,
            preferences.getBoolean("uploaded", false)
        )
    }

    private fun metadataSnapshot() = TrafficMetadataSummary(
        parsedPackets = preferences.getLong("metadata_parsed", 0),
        unparsedPackets = preferences.getLong("metadata_unparsed", 0),
        ipv4Packets = preferences.getLong("metadata_ipv4", 0),
        ipv6Packets = preferences.getLong("metadata_ipv6", 0),
        tcpPackets = preferences.getLong("metadata_tcp", 0),
        udpPackets = preferences.getLong("metadata_udp", 0),
        icmpPackets = preferences.getLong("metadata_icmp", 0),
        otherTransportPackets = preferences.getLong("metadata_other", 0),
        dnsPackets = preferences.getLong("metadata_dns", 0),
        httpPackets = preferences.getLong("metadata_http", 0),
        tlsOrQuicPackets = preferences.getLong("metadata_tls_quic", 0),
        uniqueDestinations = preferences.getInt("metadata_destinations", 0)
    )

    private fun liveMetrics(): TrafficMetrics {
        val startedAt = preferences.getLong("started_at", SystemClock.elapsedRealtime())
        val mode = preferences.getString("capture_mode", CaptureMode.CONTROLLED.apiValue)
        if (mode == CaptureMode.FULL.apiValue) {
            return TrafficMetrics(
                durationSeconds = max(0, SystemClock.elapsedRealtime() - startedAt) / 1_000,
                receivedBytes = preferences.getLong("tunnel_rx_bytes", 0),
                transmittedBytes = preferences.getLong("tunnel_tx_bytes", 0),
                receivedPackets = preferences.getLong("tunnel_rx_packets", 0),
                transmittedPackets = preferences.getLong("tunnel_tx_packets", 0)
            )
        }
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

private fun android.content.SharedPreferences.Editor.putMetadata(
    summary: TrafficMetadataSummary
): android.content.SharedPreferences.Editor =
    putLong("metadata_parsed", summary.parsedPackets)
        .putLong("metadata_unparsed", summary.unparsedPackets)
        .putLong("metadata_ipv4", summary.ipv4Packets)
        .putLong("metadata_ipv6", summary.ipv6Packets)
        .putLong("metadata_tcp", summary.tcpPackets)
        .putLong("metadata_udp", summary.udpPackets)
        .putLong("metadata_icmp", summary.icmpPackets)
        .putLong("metadata_other", summary.otherTransportPackets)
        .putLong("metadata_dns", summary.dnsPackets)
        .putLong("metadata_http", summary.httpPackets)
        .putLong("metadata_tls_quic", summary.tlsOrQuicPackets)
        .putInt("metadata_destinations", summary.uniqueDestinations)
