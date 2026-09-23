package com.anderson.wifiprevent.ui.common

fun formatAssessmentVersion(value: String?): String = when (value) {
    "rules-aggregate-v1" -> "Reglas agregadas v1"
    "legacy" -> "Versión anterior"
    null -> "No registrada"
    else -> value
}
