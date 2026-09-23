package com.anderson.wifiprevent.domain.model

data class AnalysisReceipt(
    val sessionId: String,
    val message: String,
    val riskLevel: String?,
    val riskReasons: List<String>,
    val assessmentScope: String?,
    val assessmentVersion: String?,
    val trafficAnalysisPerformed: Boolean,
    val captureMode: String,
    val indicators: List<TrafficIndicator>
)
