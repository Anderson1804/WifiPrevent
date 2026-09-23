package com.anderson.wifiprevent.domain.model

data class AnalysisHistorySummary(
    val totalSessions: Int,
    val lowRisk: Int,
    val mediumRisk: Int,
    val highRisk: Int,
    val unknownRisk: Int,
    val notEvaluated: Int,
    val controlledSessions: Int,
    val fullSessions: Int
)
