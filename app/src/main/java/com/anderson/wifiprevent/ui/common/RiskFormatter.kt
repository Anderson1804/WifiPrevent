package com.anderson.wifiprevent.ui.common

fun formatRiskLevel(
    riskLevel: String?,
    analysisPerformed: Boolean
): String {
    if (!analysisPerformed) {
        return "Riesgo no evaluado"
    }

    return when (riskLevel) {
        "low" -> "Riesgo bajo"
        "medium" -> "Riesgo medio"
        "high" -> "Riesgo alto"
        "unknown" -> "Riesgo indeterminado"
        else -> "Resultado no disponible"
    }
}