package com.anderson.wifiprevent.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.anderson.wifiprevent.domain.model.WifiSnapshot

class WifiConnectionObserver(
    context: Context,
    private val onConnectionChanged: (
        connection: WifiSnapshot?,
        status: String
    ) -> Unit
) {
    private val applicationContext = context.applicationContext

    private val connectivityManager =
        applicationContext.getSystemService(ConnectivityManager::class.java)

    private val detectedNetworks =
        linkedMapOf<Network, WifiSnapshot>()

    private var networkCallback:
            ConnectivityManager.NetworkCallback? = null

    fun start(hasLocationPermission: Boolean) {
        stop()

        val flags =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                hasLocationPermission
            ) {
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
            } else {
                0
            }

        val callback =
            object : ConnectivityManager.NetworkCallback(flags) {

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    if (networkCallback !== this) return

                    val wifiInfo = getWifiInfo(capabilities)

                    val snapshot = WifiSnapshot(
                        ssid = wifiInfo
                            ?.ssid
                            ?.takeUnless {
                                it == WifiManager.UNKNOWN_SSID ||
                                        it.isBlank()
                            }
                            ?.removeSurrounding("\""),

                        rssi = wifiInfo
                            ?.rssi
                            ?.takeIf { it in -126..-1 },

                        frequency = wifiInfo
                            ?.frequency
                            ?.takeIf { it > 0 },

                        speed = wifiInfo
                            ?.linkSpeed
                            ?.takeIf { it > 0 },

                        internetValidated =
                            capabilities.hasCapability(
                                NetworkCapabilities
                                    .NET_CAPABILITY_VALIDATED
                            ),

                        captivePortal =
                            capabilities.hasCapability(
                                NetworkCapabilities
                                    .NET_CAPABILITY_CAPTIVE_PORTAL
                            ),
                    securityType = getSecurityType(wifiInfo)
                    )
                    detectedNetworks[network] = snapshot

                    onConnectionChanged(
                        snapshot,
                        "Conexión Wi-Fi detectada"
                    )
                }

                override fun onLost(network: Network) {
                    if (networkCallback !== this) return

                    detectedNetworks.remove(network)

                    val remainingConnection =
                        detectedNetworks.values.lastOrNull()

                    onConnectionChanged(
                        remainingConnection,
                        if (remainingConnection == null) {
                            "La conexión Wi-Fi se perdió."
                        } else {
                            "Conexión Wi-Fi detectada"
                        }
                    )
                }
            }

        networkCallback = callback

        try {
            val request = NetworkRequest.Builder()
                .addTransportType(
                    NetworkCapabilities.TRANSPORT_WIFI
                )
                .addCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_VPN
                )
                .build()

            connectivityManager.registerNetworkCallback(
                request,
                callback,
                Handler(Looper.getMainLooper())
            )
        } catch (_: SecurityException) {
            networkCallback = null

            onConnectionChanged(
                null,
                "Android no permitió consultar la conexión. " +
                        "Revisa los permisos de la aplicación."
            )
        }
    }

    fun stop() {
        networkCallback?.let { callback ->
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }

        networkCallback = null
        detectedNetworks.clear()
    }

    @Suppress("DEPRECATION")
    private fun getWifiInfo(
        capabilities: NetworkCapabilities
    ): WifiInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            capabilities.transportInfo as? WifiInfo
        } else {
            applicationContext
                .getSystemService(WifiManager::class.java)
                .connectionInfo
        }
    }
    private fun getSecurityType(wifiInfo: WifiInfo?): String? {
        if (
            wifiInfo == null ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            return null
        }

        return when (wifiInfo.currentSecurityType) {
            WifiInfo.SECURITY_TYPE_OPEN -> "OPEN"
            WifiInfo.SECURITY_TYPE_WEP -> "WEP"
            WifiInfo.SECURITY_TYPE_PSK -> "WPA_WPA2_PSK"
            WifiInfo.SECURITY_TYPE_EAP -> "WPA_WPA2_ENTERPRISE"
            WifiInfo.SECURITY_TYPE_SAE -> "WPA3_SAE"
            WifiInfo.SECURITY_TYPE_OWE -> "OWE"
            else -> "OTHER_OR_UNKNOWN"
        }
    }
}