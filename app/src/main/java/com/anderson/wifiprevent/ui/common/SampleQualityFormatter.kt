package com.anderson.wifiprevent.ui.common

fun formatSampleQuality(value: String): String = when (value) {
    "adequate" -> "Adecuada"
    "limited" -> "Limitada"
    "insufficient" -> "Insuficiente"
    else -> "No disponible"
}
