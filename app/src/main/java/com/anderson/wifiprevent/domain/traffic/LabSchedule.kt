package com.anderson.wifiprevent.domain.traffic

enum class LabProfile(val label: String) {
    BURST("Ráfaga acotada"), DISTRIBUTED("Conexiones distribuidas"),
    PERIODIC("Conexiones periódicas"), UPLOAD("Subida legítima")
}

fun labOffsets(profile: LabProfile): List<Long> = when (profile) {
    LabProfile.BURST -> (0 until 20).map { it * 500L }
    LabProfile.DISTRIBUTED -> (0 until 20).map { it * 2_000L }
    LabProfile.PERIODIC -> (0 until 6).map { it * 5_000L }
    LabProfile.UPLOAD -> listOf(0L)
}

fun isPrivateLabAddress(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31)
}
