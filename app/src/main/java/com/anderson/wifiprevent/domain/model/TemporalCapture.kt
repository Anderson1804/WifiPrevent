package com.anderson.wifiprevent.domain.model

import org.json.JSONArray
import org.json.JSONObject

data class TemporalObservation(
    val offsetMs: Long, val transport: String, val category: String,
    val destinationGroup: Int, val success: Boolean
)

data class TemporalCapture(
    val startedAtUtc: String, val elapsedMs: Long, val truncated: Boolean,
    val droppedObservations: Long, val observations: List<TemporalObservation>
) {
    fun toJson() = JSONObject().apply {
        put("version", 1)
        put("started_at_utc", startedAtUtc)
        put("elapsed_ms", elapsedMs)
        put("truncated", truncated)
        put("dropped_observations", droppedObservations)
        put("observations", JSONArray().apply {
            observations.forEach { row -> put(JSONObject().apply {
                put("offset_ms", row.offsetMs); put("transport", row.transport)
                put("category", row.category); put("destination_group", row.destinationGroup)
                put("success", row.success)
            }) }
        })
    }

    companion object {
        fun fromJson(value: JSONObject?): TemporalCapture? {
            if (value == null) return null
            require(value.getInt("version") == 1)
            val rows = value.getJSONArray("observations")
            require(rows.length() <= 2048)
            return TemporalCapture(value.getString("started_at_utc"), value.getLong("elapsed_ms"),
                value.getBoolean("truncated"), value.getLong("dropped_observations"),
                (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
                    TemporalObservation(it.getLong("offset_ms"), it.getString("transport"),
                        it.getString("category"), it.getInt("destination_group"), it.getBoolean("success"))
                } })
        }
    }
}

data class DetectedEvent(
    val id: String, val type: String, val startMs: Long, val endMs: Long,
    val count: Int, val riskLevel: String, val method: String, val description: String
)

fun JSONObject.detectedEvents(): List<DetectedEvent> {
    val rows = optJSONArray("detected_events") ?: return emptyList()
    return (0 until rows.length()).map { i -> rows.getJSONObject(i).let {
        DetectedEvent(it.getString("event_id"), it.getString("event_type"), it.getLong("start_ms"),
            it.getLong("end_ms"), it.getInt("observation_count"), it.getString("risk_level"),
            it.getString("method"), it.getString("description"))
    } }
}
