package com.anderson.wifiprevent.domain.traffic

enum class CaptureMode(val apiValue: String) {
    CONTROLLED("controlled"),
    FULL("full")
}

data class VpnRoute(val address: String, val prefixLength: Int)

data class VpnCapturePlan(
    val mode: CaptureMode,
    val sessionName: String,
    val ipv4Address: String,
    val ipv4PrefixLength: Int,
    val routes: List<VpnRoute>,
    val generateValidationTraffic: Boolean,
    val requiresPacketForwarder: Boolean
)

object VpnCapturePlans {
    val controlled = VpnCapturePlan(
        mode = CaptureMode.CONTROLLED,
        sessionName = "WiFiPrevent - validación controlada",
        ipv4Address = "10.77.0.2",
        ipv4PrefixLength = 32,
        routes = listOf(VpnRoute("203.0.113.0", 24)),
        generateValidationTraffic = true,
        requiresPacketForwarder = false
    )

    val full = VpnCapturePlan(
        mode = CaptureMode.FULL,
        sessionName = "WiFiPrevent - captura completa",
        ipv4Address = "10.77.0.2",
        ipv4PrefixLength = 32,
        routes = listOf(VpnRoute("0.0.0.0", 0), VpnRoute("::", 0)),
        generateValidationTraffic = false,
        requiresPacketForwarder = true
    )

    fun forMode(mode: CaptureMode): VpnCapturePlan = when (mode) {
        CaptureMode.CONTROLLED -> controlled
        CaptureMode.FULL -> full
    }
}
