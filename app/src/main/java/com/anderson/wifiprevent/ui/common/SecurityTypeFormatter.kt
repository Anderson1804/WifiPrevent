package com.anderson.wifiprevent.ui.common

fun formatSecurityType(securityType: String?): String {
    return when (securityType) {
        "OPEN" -> "Red abierta"
        "WEP" -> "WEP"
        "WPA_WPA2_PSK" -> "WPA/WPA2 personal"
        "WPA_WPA2_ENTERPRISE" -> "WPA/WPA2 empresarial"
        "WPA3_SAE" -> "WPA3 personal"
        "OWE" -> "Red abierta con cifrado OWE"
        "OTHER_OR_UNKNOWN" -> "Otro tipo o desconocido"
        else -> "No disponible"
    }
}