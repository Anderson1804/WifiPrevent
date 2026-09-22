package com.anderson.wifiprevent.domain.model

import com.anderson.wifiprevent.domain.traffic.CaptureMode
import java.time.Instant
import java.util.UUID

enum class AnalysisSessionState {
    PREPARING,
    READY,
    ANALYZING,
    COMPLETED,
    FAILED
}

data class AnalysisSession(
    val id: String = UUID.randomUUID().toString(),
    val state: AnalysisSessionState,
    val ssid: String?,
    val captureMode: CaptureMode = CaptureMode.CONTROLLED,
    val createdAt: Instant = Instant.now()
)
