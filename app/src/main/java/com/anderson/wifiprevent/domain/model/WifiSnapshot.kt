package com.anderson.wifiprevent.domain.model

data class WifiSnapshot(
    val ssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val speed: Int?,
    val internetValidated: Boolean,
    val captivePortal: Boolean,
    val securityType: String?
)