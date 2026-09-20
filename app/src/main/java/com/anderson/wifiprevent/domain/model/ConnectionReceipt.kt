package com.anderson.wifiprevent.domain.model

data class ConnectionReceipt(
    val id: String,
    val message: String,
    val riskLevel: String?,
    val riskReasons: List<String>,
    val analysisPerformed: Boolean
)
