package com.anderson.wifiprevent.domain.model

data class HistoryEntry(
    val id: String,
    val receivedAt: String,
    val ssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val speed: Int?,
    val internetValidated: Boolean,
    val captivePortal: Boolean
)

